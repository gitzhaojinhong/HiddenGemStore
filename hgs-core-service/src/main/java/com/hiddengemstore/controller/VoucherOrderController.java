package com.hiddengemstore.controller;

import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.service.IVoucherOrderService;
import com.hiddengemstore.uitls.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券订单控制器
 * @author : ZhaoJH
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;


    /**
     * 抢优惠卷下单
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    @PostMapping("/seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        return voucherOrderService.seckillVoucher(voucherId,userId);
    }
}
