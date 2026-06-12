package com.hiddengemstore.kafka.producer;

import com.hiddengemstore.kafka.message.SeckillVoucherInvalidationMessage;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.producer.AbstractProducerHandler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀券缓存失效消息的 Kafka 生产者
 *
 * <p>当秒杀券数据发生变更（如库存更新、券信息修改等）时，通过该生产者向 Kafka 集群
 * 广播缓存失效消息，通知所有消费节点清除本地缓存，保证数据一致性。</p>
 *
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>指数退避重试</b>：发送失败后按 {@code 2^retryCount * initialBackoffMillis} 的策略
 *       进行退避重试，最大重试次数由 {@code seckill.cache.invalidate.retry.maxAttempts} 控制</li>
 *   <li><b>死信队列（DLQ）</b>：重试耗尽后将消息转入 DLQ，防止消息丢失，并支持后续人工或自动重放</li>
 *   <li><b>监控指标</b>：通过 Micrometer 上报发送成功/失败/重试/DLQ 等计数器，便于 Grafana 等监控</li>
 *   <li><b>审计日志</b>：关键操作（DLQ 投递、DLQ 重放成功）记录到独立的 AUDIT 日志文件</li>
 * </ul>
 *
 * @author ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherInvalidationProducer extends AbstractProducerHandler<MessageExtend<SeckillVoucherInvalidationMessage>> {
    /**
     * 构造注入 KafkaTemplate，交由父类 {@link AbstractProducerHandler} 管理发送生命周期。
     *
     * @param kafkaTemplate Kafka 发送模板，泛型 key 为 String，value 为消息包装体
     */
    public SeckillVoucherInvalidationProducer(final KafkaTemplate<String, MessageExtend<SeckillVoucherInvalidationMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }

    /** 消息头键名：记录当前已重试次数 */
    private final static String RETRY_COUNT = "retryCount";

    /** 死信队列后缀，追加到原 topic 名称后形成 DLQ topic（如 "topic.DLQ"） */
    private final static String DLQ = ".DLQ";

    @Resource
    private PropertiesLoaderSupport propertiesLoaderSupport;

    /** Micrometer 指标注册器，用于上报发送成功率、重试次数等监控计数器 */
    @Resource
    private MeterRegistry meterRegistry;

    /** 最大重试次数，超过后消息将被投入死信队列 */
    @Value("${seckill.cache.invalidate.retry.maxAttempts:3}")
    private int retryMaxAttempts;

    /** 初始退避时间（毫秒），指数退避公式：{@code backoff = 2^retryCount * initialBackoffMillis} */
    @Value("${seckill.cache.invalidate.retry.initialBackoffMillis:200}")
    private long initialBackoffMillis;

    /** 最大退避时间（毫秒），防止单次退避时间过长 */
    @Value("${seckill.cache.invalidate.retry.maxBackoffMillis:800}")
    private long maxBackoffMillis;

    /** 审计日志记录器，输出到独立的 AUDIT 日志文件，用于追踪 DLQ 投递和重放等关键操作 */
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    /**
     * 发送失败后的回调处理，实现指数退避重试 + 死信队列兜底策略。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>记录错误日志并上报失败指标</li>
     *   <li>若当前 topic 已是 DLQ，则仅记录指标后直接返回，防止 DLQ 递归</li>
     *   <li>从消息头中读取已重试次数，若未超限则按指数退避等待后重新发送</li>
     *   <li>若重试次数已超限，将消息转入死信队列（原 topic + ".DLQ"）并记录审计日志</li>
     * </ol>
     *
     * @param topic     目标 Kafka topic
     * @param message   待发送的消息包装体
     * @param throwable 发送失败的异常，可能为 null
     */
    @Override
    protected void afterSendFailure(String topic, MessageExtend<SeckillVoucherInvalidationMessage> message, Throwable throwable) {
        SeckillVoucherInvalidationMessage body = message.getMessageBody();
        Long voucherId = body.getVoucherId();
        String reason = body.getReason();
        String errMsg = throwable == null ? "unknown" : throwable.getMessage();
        log.error("SeckillVoucherInvalidation send failed, topic={}, uuid={}, key={}, voucherId={}, reason={}, error= {}",
                topic, message.getUuid(), message.getKey(), voucherId, reason, errMsg, throwable);
        // 防止 DLQ 递归：如果死信队列也发送失败，直接统计到失败指标中
        if (topic.contains(DLQ)) {
            safeInc("seckill_invalidation_dlq", "reason", "send_failures");
            return;
        }else {
            // 指标：失败计数
            safeInc("seckill_invalidation_send_failures", "topic", topic);
        }

        // 从消息头中读取已重试次数
        Map<String, String> headers = message.getHeaders();
        headers = headers == null ? new HashMap<>(0) :new HashMap<>(headers);
        int retryCount = 0;
        try {
            if (headers.containsKey(RETRY_COUNT)) {
                retryCount = Integer.parseInt(headers.get(RETRY_COUNT));
            }
        } catch (Exception ignore){}
        // 重试次数未超限，执行指数退避重试
        if (retryCount<=retryMaxAttempts) {
            // 指数退避公式：backoff = 2^retryCount * initialBackoffMillis，上限为 maxBackoffMillis
            long backoff = Math.min(initialBackoffMillis * (1L << retryCount), maxBackoffMillis);
            // 更新消息头中的重试次数和最近一次错误信息
            headers.put(RETRY_COUNT, String.valueOf(retryCount+1));
            headers.put("lastError",truncate(errMsg));
            message.setHeaders(headers);
            log.warn("Retry sending cache invalidation, topic={}, uuid={}, voucherId={}, retryCount={}, backoffMs={}",
                    topic, message.getUuid(), voucherId, retryCount + 1, backoff);
            // 上报重试指标
            safeInc("seckill_invalidation_send_retries", "topic", topic);
            // 按指数退避策略等待
            sleepQuietly(backoff);
            // 异步重试：若再失败会再次进入本方法，直到超过最大重试次数
            sendRecord(topic, message);
            return;
        }

        // 重试次数已超限，将消息转入死信队列（DLQ）兜底
        final String dlqReason = "send_invalid_cache_broadcast_failed: " + truncate(errMsg);
        try {
            // 发送到死信队列
            sendToDlq(topic, body, dlqReason);
            log.warn("Send cache invalidation to DLQ, originalTopic={}, uuid={}, voucherId={}, dlqReason={}",
                    topic, message.getUuid(), voucherId, dlqReason);
            // 记录 DLQ 投递审计日志
            auditLog.warn("DLQ_PUBLISH|topic={}|uuid={}|key={}|voucherId={}|reason={}",
                    topic, message.getUuid(), message.getKey(), voucherId, dlqReason);
            // 上报 DLQ 投递指标
            safeInc("seckill_invalidation_send_dlq", "topic", topic);
        } catch (Exception e) {
            log.error("Send cache invalidation to DLQ failed, originalTopic={}, uuid={}, voucherId={}, error={}",
                    topic, message.getUuid(), voucherId, e.getMessage(), e);
            // DLQ 投递也失败，记录指标（消息可能丢失，需人工介入）
            safeInc("seckill_invalidation_send_dlq_failures", "topic", topic);
        }

    }

    /**
     * 发送成功后的回调处理，上报成功指标，并记录 DLQ 重放成功的审计日志。
     *
     * <p>当消息头中携带 {@code dlqReplayCount=1} 标记时，说明该消息是从死信队列重放成功的，
     * 此时额外上报重放成功指标并写入审计日志，便于运维追踪重放效果。</p>
     *
     * @param result Kafka 发送结果，包含 topic、offset 等元数据
     */
    @Override
    protected void afterSendSuccess(SendResult<String, MessageExtend<SeckillVoucherInvalidationMessage>> result) {
        super.afterSendSuccess(result);
        // 提取发送成功的 topic 和原始消息
        String topic = result.getRecordMetadata().topic();
        MessageExtend<SeckillVoucherInvalidationMessage> message = result.getProducerRecord().value();
        // 判断该消息是否为 DLQ 重放消息（通过消息头 dlqReplayCount 标识）
        boolean dlqReplay = message != null && message.getHeaders() != null && "1".equals(message.getHeaders().getOrDefault("dlqReplayCount", "0"));
        // 上报发送成功指标
        safeInc("seckill_invalidation_send_success", "topic", topic);
        // DLQ 重放成功的消息，额外上报重放成功指标并记录审计日志
        if (dlqReplay) {
            safeInc("seckill_invalidation_dlq_replay_success", "topic", topic);
            auditLog.info("DLQ_REPLAY_SUCCESS|topic={}|uuid={}|key={}|voucherId={}",
                    topic, message.getUuid(), message.getKey(), message.getMessageBody().getVoucherId());
        }
    }


    /**
     * 将字符串截断到最大 256 个字符，防止超长错误信息写入消息头或日志。
     *
     * @param s 待截断的字符串，允许为 null
     * @return 截断后的字符串，若输入为 null 则返回 null
     */
    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 256 ? s : s.substring(0, 256);
    }

    /**
     * 静默休眠指定毫秒数，用于重试间的指数退避等待。
     * 若线程被中断，会恢复中断标志而不抛出异常。
     *
     * @param backoffMs 休眠时间（毫秒）
     */
    private void sleepQuietly(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 安全地递增 Micrometer 计数器。若 meterRegistry 未注入或上报异常则静默忽略，
     * 保证监控故障不会影响核心业务流程。
     *
     * @param name     计数器名称，如 "seckill_invalidation_send_success"
     * @param tagKey   标签键，用于维度聚合
     * @param tagValue 标签值
     */
    private void safeInc(String name, String tagKey, String tagValue) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter(name, tagKey, tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }
}
