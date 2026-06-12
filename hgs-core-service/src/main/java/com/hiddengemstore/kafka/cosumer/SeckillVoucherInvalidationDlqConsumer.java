package com.hiddengemstore.kafka.cosumer;

import com.alibaba.fastjson.JSON;
import com.hiddengemstore.consumer.AbstractConsumerHandler;
import com.hiddengemstore.kafka.message.SeckillVoucherInvalidationMessage;
import com.hiddengemstore.message.MessageExtend;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static com.hiddengemstore.constant.Constant.SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC;
import static com.hiddengemstore.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * 秒杀券缓存失效消息的死信队列（DLQ）消费者
 *
 * <p>当 {@link com.hiddengemstore.kafka.producer.SeckillVoucherInvalidationProducer}
 * 在重试耗尽后将消息投入 DLQ 时，由本消费者负责接收并处理这些失败消息。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>广播消费模式</b>：每个实例使用随机 groupId 订阅 DLQ topic，确保所有实例都能感知到
 *       死信消息，实现高可用监控——任何一个实例存活都能保证消息被记录和告警</li>
 *   <li><b>无副作用</b>：当前仅记录审计日志和上报指标，不执行任何业务补偿操作。
 *       即使多个实例重复消费同一条消息，也不会对系统状态造成不良影响</li>
 *   <li><b>即时感知</b>：每个实例都能立即知道产生了死信消息，便于运维快速介入排查</li>
 * </ul>
 *
 * <h3>后续扩展</h3>
 * <p>可在此基础上实现自动重放逻辑：将 DLQ 消息重新发送到原始 topic，并在消息头中
 * 携带 {@code dlqReplayCount} 标记，由生产者侧的重放检测逻辑（参见
 * {@code SeckillVoucherInvalidationProducer#afterSendSuccess}）进行追踪。</p>
 *
 * @author ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherInvalidationDlqConsumer extends AbstractConsumerHandler<SeckillVoucherInvalidationMessage> {

    /** Micrometer 指标注册器，用于上报 DLQ 消费计数和异常跳过等指标 */
    @Resource
    private MeterRegistry meterRegistry;

    /** 审计日志记录器，输出到独立的 AUDIT 日志文件，用于追踪死信消息详情 */
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");


    /**
     * 构造函数，指定消息体反序列化类型为 {@link SeckillVoucherInvalidationMessage}。
     */
    public SeckillVoucherInvalidationDlqConsumer() {
        super(SeckillVoucherInvalidationMessage.class);
    }

    /**
     * Kafka 消息入口，接收 DLQ topic 上的原始消息并交给父类解析。
     *
     * <p>采用广播消费模式（随机 groupId），确保所有实例都能感知死信消息：</p>
     * <ul>
     *   <li>高可用监控：任一实例存活即可保证 DLQ 消息被记录和告警</li>
     *   <li>无副作用：仅写日志和指标，重复消费不会影响系统状态</li>
     *   <li>即时感知：每个实例都能立即知道产生了死信消息</li>
     * </ul>
     *
     * @param value          消息原始 JSON 字符串
     * @param headers        Kafka 消息头，包含重试次数、错误信息等上下文
     * @param key            消息键（voucherId），可能为 null
     * @param acknowledgment 手动确认回调，消费完成后调用以提交 offset
     */
    @KafkaListener(
            topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC + ".DLQ"},
            groupId = SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-seckill_voucher_cache_invalidation_dlq-${random.uuid}"
    )
    public void onMessage(String value,
                          @Headers Map<String, Object> headers,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          Acknowledgment acknowledgment) {
        consumeRaw(value, key, headers);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    /**
     * DLQ 消息的核心处理逻辑：记录审计日志并上报监控指标。
     *
     * <p>当前仅做日志记录和指标上报，不执行业务补偿。后续可扩展自动重放功能，
     * 将消息重新发送到原始 topic 并标记 {@code dlqReplayCount}。</p>
     *
     * @param message 已反序列化的消息包装体
     */
    @Override
    protected void doConsume(MessageExtend<SeckillVoucherInvalidationMessage> message) {
        SeckillVoucherInvalidationMessage body = message.getMessageBody();
        // 校验消息载荷完整性，voucherId 为空则跳过处理
        if (Objects.isNull(body.getVoucherId())) {
            log.warn("DLQ消息载荷为空或voucherId缺失, uuid={}", message.getUuid());
            safeInc("seckill_invalidation_dlq_replay_skipped");
            return;
        }

        // 上报 DLQ 消费指标
        safeInc("seckill_invalidation_dlq");

        // 记录完整的死信消息到审计日志，便于人工排查和后续重放
        auditLog.error("SECKILL_INVALIDATION_DLQ | message={}", JSON.toJSONString(message));
    }

    /**
     * 安全地递增 Micrometer 计数器，异常时静默忽略以保证不影响主流程。
     *
     * @param name 计数器名称
     */
    private void safeInc(String name) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter(name, "reason", "invalid_payload").increment();
            }
        } catch (Exception ignore) {
        }
    }
}
