package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.VoucherOrder;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.message.MessageExtend;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result<Long> seckillVoucher(Long voucherId,Long userId);

    void createVoucherOrder(MessageExtend<SeckillVoucherMessage> message);
}
