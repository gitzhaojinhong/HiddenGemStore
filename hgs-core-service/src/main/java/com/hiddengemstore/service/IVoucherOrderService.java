package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.VoucherOrder;
import com.hiddengemstore.entity.dto.CancelVoucherOrderDto;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.message.MessageExtend;
import jakarta.validation.Valid;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result<Long> seckillVoucher(Long voucherId,Long userId);

    void createVoucherOrder(MessageExtend<SeckillVoucherMessage> message);


    Boolean cancel(@Valid CancelVoucherOrderDto cancelVoucherOrderDto);

    /**
     * 自动发券给最早的订阅用户
     * 业务流程：
     * 1. 查询秒杀券信息并验证有效性
     * 2. 加载库存到Redis
     * 3. 查找最早的符合条件的候选用户
     * 4. 向候选用户发券
     *
     * @param voucherId 秒杀券ID
     * @param excludeUserId 排除的用户ID（通常是刚取消订单的用户）
     * @return 是否成功发券
     */
    boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId);

}
