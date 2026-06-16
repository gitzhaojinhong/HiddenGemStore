package com.hiddengemstore.kafka.cosumer;

import com.hiddengemstore.consumer.AbstractConsumerHandler;
import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.enums.BaseCode;
import com.hiddengemstore.enums.BusinessType;
import com.hiddengemstore.enums.LogType;
import com.hiddengemstore.enums.SeckillVoucherOrderOperate;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.kafka.rollbackredis.RedisVoucherDataRollback;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.service.IVoucherOrderService;
import com.hiddengemstore.service.IVoucherReconcileLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 秒杀券消费者,处理秒杀券下单消息。
 * @author : ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherConsumer extends AbstractConsumerHandler<SeckillVoucherMessage> {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private RedisVoucherDataRollback redisVoucherDataRollback;
    @Resource
    private IVoucherReconcileLogService voucherReconcileLogService;

    public SeckillVoucherConsumer() {
        super(SeckillVoucherMessage.class);
    }


    // 消息延迟阈值，单位毫秒，如果消息超过该阈值才到达，则丢弃并回滚数据
    public static Long MESSAGE_DELAY_TIME = 10000L;

    @Override
    protected void doConsume(MessageExtend<SeckillVoucherMessage> message) {
        voucherOrderService.createVoucherOrder(message);
    }

    /**
     * 消费前置过滤：若消息延迟超过阈值则丢弃并回滚，同时记录对账日志。
     * 返回 true 继续消费；返回 false 中断后续消费流程。
     * <p>
     * 延迟阈值设计：默认 10 秒，保障“秒杀链路”时效性，超过则视为“过期消息”直接回滚，避免订单在活动结束或状态不一致时落库
     * 回滚逆向参数：以消息体中的 beforeQty/afterQty/changeQty 反向传入回滚组件，复原 Redis 中的库存与用户集合状态，同时写回“恢复日志”
     * 幂等与一致性：在前置阶段就丢弃超时消息，减少后续 DB 写入与对账复杂度；配合后续 doConsume 的事务与失败回滚，实现端到端的最终一致性保障
     * 容错与观测：任何对账日志写入失败仅记录告警，不影响主流程；traceId 用于串联 Kafka 消息与 Redis Lua 扣减日志，便于审计
     */
    @Override
    protected boolean beforeConsume(MessageExtend<SeckillVoucherMessage> message) {
        LocalDateTime producerTime = message.getProducerTime();
        long delayTime = Duration.between(producerTime, LocalDateTime.now()).toMillis();
        //如果消息超时时间达到了阈值（10秒）
        if (delayTime>MESSAGE_DELAY_TIME) {
            log.info("消费到kafka的创建优惠券消息延迟时间大于了 {} 毫秒 此订单消息被丢弃 订单号 : {}",
                    delayTime,message.getMessageBody().getOrderId());
            long traceId = snowflakeIdGenerator.nextId();
            // 回滚redis
            redisVoucherDataRollback.rollbackRedisVoucherData(
                    SeckillVoucherOrderOperate.YES,
                    traceId,
                    message.getMessageBody().getVoucherId(),
                    message.getMessageBody().getUserId(),
                    message.getMessageBody().getOrderId(),
                    // 这是回滚操作，所以redis中扣减前和扣减后的数量要和消息中的反过来
                    message.getMessageBody().getAfterQty(),
                    message.getMessageBody().getChangeQty(),
                    message.getMessageBody().getBeforeQty()
            );
            // 对账日志：异常-消息延迟丢弃,出现异常不影响主流程
            try {
                voucherReconcileLogService.saveReconcileLog(LogType.RESTORE.getCode(),
                        BusinessType.TIMEOUT.getCode(),
                        "message delayed " + delayTime + "ms, rollback redis",
                        traceId,
                        message);
            } catch (Exception e) {
                log.warn("保存对账日志失败(延迟丢弃)", e);
            }
            return false;
        }
        return true;
    }

    @Override
    protected void afterConsumeSuccess(MessageExtend<SeckillVoucherMessage> message) {
        super.afterConsumeSuccess(message);
        // TODO:统计以及其他操作
    }

    /**
     * 消费失败后处理：回滚 Redis 数据并记录对账日志
     * 作用：当 Kafka 消息消费异常时，恢复 Redis 中的库存和用户购买状态，确保数据一致性
     * 设计意图：通过区分"订单已存在"和"其他异常"两种场景，决定是否需要回滚用户集合，避免重复回滚导致的数据错误
     * @param message 消费失败的 Kafka 消息扩展对象，包含券ID、用户ID、订单ID等关键信息
     * @param throwable 消费过程中抛出的异常对象，用于判断失败原因和记录详细日志
     */
    @Override
    protected void afterConsumeFailure(MessageExtend<SeckillVoucherMessage> message, Throwable throwable) {
        super.afterConsumeFailure(message, throwable);
        // 默认需要回滚用户购买记录（从用户集合中移除）
        SeckillVoucherOrderOperate seckillVoucherOrderOperate = SeckillVoucherOrderOperate.YES;
        // 若异常类型为 HGSFrameException 且业务码等于 BaseCode.VOUCHER_ORDER_EXIST，说明“一人一单”已存在订单，设置操作标识为 NO（不删除已购标记）
        if (throwable instanceof HGSFrameException hgsFrameException) {
            if (Objects.nonNull(hgsFrameException.getCode()) &&
                    hgsFrameException.getCode().equals(BaseCode.VOUCHER_ORDER_EXIST.getCode())){
                seckillVoucherOrderOperate = SeckillVoucherOrderOperate.NO;
            }
        }
        // 生成追踪ID，串联 Redis 回滚操作和对账日志
        long traceId = snowflakeIdGenerator.nextId();
        // 执行 Redis 数据回滚：恢复库存、移除用户购买记录（根据操作码决定）
        redisVoucherDataRollback.rollbackRedisVoucherData(
                seckillVoucherOrderOperate,
                traceId,
                message.getMessageBody().getVoucherId(),
                message.getMessageBody().getUserId(),
                message.getMessageBody().getOrderId(),
                message.getMessageBody().getAfterQty(),
                message.getMessageBody().getChangeQty(),
                message.getMessageBody().getBeforeQty()
        );
        // 保存对账日志：记录消费失败详情，便于后续审计和问题排查
        try {
            String detail = throwable == null ? "consume failed" : ("consume failed: " + throwable.getMessage());
            voucherReconcileLogService.saveReconcileLog(LogType.RESTORE.getCode(),
                    BusinessType.FAIL.getCode(),
                    detail,
                    traceId,
                    message
            );
        } catch (Exception e) {
            log.warn("保存对账日志失败(消费失败)", e);
        }
    }
}
