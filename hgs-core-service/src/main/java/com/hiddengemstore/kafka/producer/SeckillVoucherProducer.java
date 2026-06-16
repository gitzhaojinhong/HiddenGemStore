package com.hiddengemstore.kafka.producer;

import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.enums.SeckillVoucherOrderOperate;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.kafka.rollbackredis.RedisVoucherDataRollback;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.producer.AbstractProducerHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 异步消费秒杀券生产者
 * @author : ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherProducer extends AbstractProducerHandler<MessageExtend<SeckillVoucherMessage>> {
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private RedisVoucherDataRollback redisVoucherData;

    public SeckillVoucherProducer(KafkaTemplate<String, MessageExtend<SeckillVoucherMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }


    /**
     * Kafka消息发送失败后的回滚处理
     * 执行Redis库存和用户购买记录的回滚操作，确保数据一致性
     * @param topic 主题名称
     * @param message 秒杀券消息
     * @param throwable 异常信息
     */
    @Override
    protected void afterSendFailure(final String topic, final MessageExtend<SeckillVoucherMessage> message, final Throwable throwable) {
        super.afterSendFailure(topic, message, throwable);
        long traceId = snowflakeIdGenerator.nextId();
        redisVoucherData.rollbackRedisVoucherData(
                SeckillVoucherOrderOperate.YES,
                traceId,
                message.getMessageBody().getVoucherId(),
                message.getMessageBody().getUserId(),
                message.getMessageBody().getOrderId(),
                // 这是回滚操作，所以redis中扣减前和扣减后的数量要和消息中的反过来
                message.getMessageBody().getAfterQty(),
                message.getMessageBody().getChangeQty(),
                message.getMessageBody().getBeforeQty());
    }
}
