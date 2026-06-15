package com.hiddengemstore.kafka.cosumer;

import com.hiddengemstore.consumer.AbstractConsumerHandler;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.service.IVoucherOrderService;
import jakarta.annotation.Resource;

/**
 * 秒杀券消费者,处理秒杀券下单消息。
 * @author : ZhaoJH
 */
public class SeckillVoucherConsumer extends AbstractConsumerHandler<SeckillVoucherMessage> {
    @Resource
    private IVoucherOrderService voucherOrderService;

    public SeckillVoucherConsumer() {
        super(SeckillVoucherMessage.class);
    }




    @Override
    protected void doConsume(MessageExtend<SeckillVoucherMessage> message) {
        voucherOrderService.createVoucherOrder(message);
    }
}
