package com.hiddengemstore.controller;

import com.hiddengemstore.entity.dto.CancelVoucherOrderDto;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.enums.RateLimitScene;
import com.hiddengemstore.execute.RateLimitHandler;
import com.hiddengemstore.service.ISeckillAccessTokenService;
import com.hiddengemstore.service.IVoucherOrderService;
import com.hiddengemstore.uitls.UserHolder;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券订单控制器
 * @author : ZhaoJH
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RateLimitHandler rateLimitHandler;
    @Resource
    private ISeckillAccessTokenService accessTokenService;


    /**
     * 获取访问令牌
     * @param voucherId 优惠券ID
     * @return 访问令牌
     */
    @GetMapping("/seckill/token/{id}")
    public Result<String> issueSeckillAccessToken(@PathVariable("id") Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 限流
        rateLimitHandler.execute(voucherId, userId, RateLimitScene.ISSUE_TOKEN);
        // 生成申请访问令牌（获得操作权限）
        String token = accessTokenService.issueAccessToken(voucherId, userId);
        return Result.ok(token);
    }


    /**
     * 抢优惠卷下单
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    @PostMapping("/seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId,
                                       @RequestParam(name = "accessToken", required = false) String accessToken) {
        Long userId = UserHolder.getUser().getId();
        // 限流
        rateLimitHandler.execute(voucherId, userId, RateLimitScene.SECKILL_ORDER);
        // 若访问令牌功能启用，则消费访问令牌
        if (accessTokenService.isEnabled()) {
            if (accessToken == null || !accessTokenService.validateAndConsume(voucherId, userId, accessToken)) {
                return Result.fail("令牌校验失败或令牌已失效");
            }
        }
        // 通过限流和访问令牌消费后，才能下单
        return voucherOrderService.seckillVoucher(voucherId,userId);
    }

    /**
     * 取消订单
     * @param cancelVoucherOrderDto 取消订单参数
     * @return 是否成功
     */
    @PostMapping("/cancel")
    public Result<Boolean> cancel(@Valid @RequestBody CancelVoucherOrderDto cancelVoucherOrderDto) {
        return Result.ok(voucherOrderService.cancel(cancelVoucherOrderDto));
    }
}
