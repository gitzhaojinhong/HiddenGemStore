package com.hiddengemstore.kafka.cosumer;

import com.hiddengemstore.cache.SeckillVoucherLocalCache;
import com.hiddengemstore.consumer.AbstractConsumerHandler;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.kafka.message.SeckillVoucherInvalidationMessage;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.annotation.ServiceLock;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
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
import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_LOCK;

/**
 * Kafka 消费者：接收“秒杀券缓存失效”广播
 *
 * @author : ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherInvalidationConsumer extends AbstractConsumerHandler<SeckillVoucherInvalidationMessage> {
    @Resource
    private RedisCache redisCache;
    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;
    /*
        Micrometer监控框架的指标注册中心，用于收集和上报应用性能指标。
        通过注入该对象，可在代码中记录自定义指标，供Prometheus等监控系统采集分析。
    */
    @Resource
    private MeterRegistry meterRegistry;

    public SeckillVoucherInvalidationConsumer() {
        super(SeckillVoucherInvalidationMessage.class);
    }

    /**
     * 广播效果：
     * random.uuid 每个实例唯一，意味着每个实例都在“独立的消费组”，
     * 从而实现“广播消费”——————>每个实例都会收到同一条消息（而不是在同一组内做负载均衡只给其中一个实例）。
     *
     * @param value          消息内容
     * @param headers        消息头
     * @param key            消息的 key
     * @param acknowledgment 确认
     */
    @KafkaListener(
            topics = {SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC},
            groupId = SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-seckill_voucher_cache_invalidation-${random.uuid}"
    )
    public void onMessage(
            String value,
            @Headers Map<String, Object> headers,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,//从消息头中单独取出来的
            Acknowledgment acknowledgment) {
        consumeRaw(value, key, headers);
        // 手动提交Kafka偏移量，确认消息已处理成功，防止消息丢失或重复消费。配置文件中ack-mode: manual_immediate，所以这是必须的
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    @Override
    protected void doConsume(MessageExtend<SeckillVoucherInvalidationMessage> message) {
        SeckillVoucherInvalidationMessage body = message.getMessageBody();
        if (Objects.isNull(body.getVoucherId())) {
            log.warn("收到缓存失效消息但载荷为空或voucherId缺失, uuid={}", message.getUuid());
            return;
        }
        Long voucherId = body.getVoucherId();

        ((SeckillVoucherInvalidationConsumer) AopContext.currentProxy()).delCache(voucherId);
    }

    /*
          消费端重复删除Redis缓存的原因（即使生产者已删除）：
          1. 【消息传递不可靠性】
             - 生产者删除Redis与发送Kafka消息不是原子操作，存在时序间隙
             - Kafka消息可能因网络分区、消费者宕机、重启等原因丢失或延迟
             - 若仅依赖生产者删除，未收到消息的实例其Redis中的脏数据将永久残留
          2. 【节点状态自治原则】
             - 每个服务实例应基于自身收到的权威信号（Kafka消息）管理缓存状态
             - 不能假设"生产者已删除Redis"这一事实在所有实例间即时同步
             - 消费端删除是节点对"数据已变更"信号的确认与状态同步动作
          3. 【幂等操作的安全网设计】
             - Redis的DEL命令是幂等的，删除不存在的键无副作用
             - 重复删除为消息丢失、重复消费、实例重启等异常场景提供容错
             - 以极小代价（一次网络IO）换取系统最终一致性的可靠保障
          4. 【缓存访问的局部性】
             - 即使连接同一Redis集群，不同实例的缓存加载时机不同
             - 实例可能在生产者删除前一刻刚加载旧数据到本地，此时Redis虽被删，
               但该实例仍持有脏数据的本地副本，需通过消费端删除触发重新加载
          设计哲学：生产者删除追求"尽快"一致，消费端删除确保"最终"一致。
          这是分布式缓存一致性中"双删"模式的经典实践。
     */

    /*
        消费端缓存清理方法 - 操作顺序与加锁原因：
        【操作顺序：先清本地，后删Redis】
        1. 优先清本地缓存：立即切断当前实例的旧数据源，后续读请求触发回源，尽快读到新数据
        2. 再删Redis缓存：清理共享缓存层，通知其他实例或当前实例后续请求
        【为何不先删Redis？】
        - 先删除Redis可完全避免“业务读在中间插入导致旧数据重载”问题
        - 但若先删Redis，本地旧数据仍会服务一段时间，延长不一致窗口
        【加写锁的核心作用】
        正是为了防止"先清本地→后删Redis"之间的极短时间窗口（约1ms）内，
        业务读请求插入并读到Redis旧数据重载到本地。
        锁确保：清本地 → 删Redis → 业务读 严格串行化。
        <br>
        此顺序与生产者保持一致，形成对称设计，是吞吐量与一致性的最佳平衡。
     */
    @ServiceLock(lockType = LockType.Write, name = UPDATE_SECKILL_VOUCHER_LOCK, keys = {"#voucherId"})
    private void delCache(Long voucherId) {
        RedisKeyBuild seckillVoucherRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        // 1) 失效本地缓存
        seckillVoucherLocalCache.invalidate(seckillVoucherRedisKey.getRelKey());
        // 2) 删除Redis缓存（券详情、库存、空值）——幂等删除
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId));
    }


    /**
     * 消费失败处理
     *
     * @param message   消费失败的消息
     * @param throwable 失败异常
     */
    @Override
    protected void afterConsumeFailure(final MessageExtend<SeckillVoucherInvalidationMessage> message, final Throwable throwable) {
        super.afterConsumeFailure(message, throwable);
        log.warn("删除Redis缓存失败 voucherId={}", message.getMessageBody().getVoucherId(), throwable);
        safeInc(errorTag(throwable));
    }

    /**
     * 安全上报消费失败指标（异常隔离，不影响主流程）
     *
     * @param tagValue 错误类型标签值
     */
    private void safeInc(String tagValue) {
        try {
            // 空指针防护：确保MeterRegistry已注入
            if (meterRegistry != null) {
                // 上报消费失败指标，以错误类型为标签，便于监控告警和故障定位
                meterRegistry.counter("seckill_invalidation_consume_failures", "error", tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * 提取错误类型标签
     *
     * @param t 捕获的异常对象
     * @return 异常类名作为标签，null时返回"unknown"
     */
    private String errorTag(Throwable t) {
        return t == null ? "unknown" : t.getClass().getSimpleName();
    }
}
