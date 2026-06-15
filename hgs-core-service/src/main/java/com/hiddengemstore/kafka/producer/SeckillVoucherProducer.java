package com.hiddengemstore.kafka.producer;

import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.producer.AbstractProducerHandler;
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
    public SeckillVoucherProducer(KafkaTemplate<String, MessageExtend<SeckillVoucherMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }
}
