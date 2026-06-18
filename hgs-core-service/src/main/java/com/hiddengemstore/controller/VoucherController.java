package com.hiddengemstore.controller;

import com.hiddengemstore.entity.dto.*;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
import com.hiddengemstore.entity.vo.GetSubscribeStatusVo;
import com.hiddengemstore.service.ISeckillVoucherService;
import com.hiddengemstore.service.IVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠卷控制器
 * @author : ZhaoJH
 */
@RestController
@RequestMapping("/voucher")
@Tag(name = "优惠券接口",description = "优惠券相关接口")
public class VoucherController {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;


    /**
     * 获取秒杀优惠券
     * @param getSeckillVoucherDto 获取优惠券参数
     * @return 优惠券信息
     */
    @Operation(summary = "获取秒杀优惠券", description = "根据券ID查询秒杀券详情")
    @PostMapping("/get")
    public Result<SeckillVoucherFullModel> get(@Valid @RequestBody GetSeckillVoucherDto getSeckillVoucherDto){//@Valid 触发参数校验
        return Result.ok(seckillVoucherService.queryByVoucherId(getSeckillVoucherDto.getVoucherId()));
    }

    /**
     * 添加秒杀优惠券
     * @param seckillVoucherDto 添加秒杀优惠券参数
     * @return 优惠券ID
     */
    @PostMapping("/seckill")
    public Result<Long> addSeckillVoucher(@Valid @RequestBody SeckillVoucherDto seckillVoucherDto) {
        final Long voucherId = voucherService.addSeckillVoucher(seckillVoucherDto);
        return Result.ok(voucherId);
    }
    /**
     * 添加普通优惠券
     * @param voucherDto 添加普通优惠券参数
     * @return 优惠券ID
     */
    @PostMapping
    public Result<Long> addVoucher(@Valid @RequestBody VoucherDto voucherDto) {
        final Long voucherId = voucherService.addVoucher(voucherDto);
        return Result.ok(voucherId);
    }
    /**
     * 更新秒杀优惠券
     * @param updateSeckillVoucherDto 更新秒杀优惠券参数
     * @return 更新结果
     */
    @PostMapping("/update/seckill")
    public Result<Void> updateSeckillVoucher(@Valid @RequestBody UpdateSeckillVoucherDto updateSeckillVoucherDto){
        voucherService.updateSeckillVoucher(updateSeckillVoucherDto);
        return Result.ok();
    }

    /**
     * 更新秒杀优惠券库存
     * @param updateSeckillVoucherDto 更新秒杀优惠券库存参数
     * @return 更新结果
     */
    @PostMapping("/update/seckill/stock")
    public Result<Void> updateSeckillVoucherStock(@Valid @RequestBody UpdateSeckillVoucherStockDto updateSeckillVoucherDto) {
        voucherService.updateSeckillVoucherStock(updateSeckillVoucherDto);
        return Result.ok();
    }

    /**
     * 订阅优惠券
     * @param voucherSubscribeDto 订阅优惠券参数
     * @return 订阅结果
     */
    @PostMapping("/subscribe")
    public Result<Void> subscribe(@Valid @RequestBody VoucherSubscribeDto voucherSubscribeDto){
        voucherService.subscribe(voucherSubscribeDto);
        return Result.ok();
    }

    /**
     * 取消订阅优惠券
     * @param voucherSubscribeDto 取消订阅优惠券参数
     * @return 取消订阅结果
     */
    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(@Valid @RequestBody VoucherSubscribeDto voucherSubscribeDto){
        voucherService.unsubscribe(voucherSubscribeDto);
        return Result.ok();
    }

    /**
     * 获取订阅优惠券状态
     * @param voucherSubscribeDto 获取订阅优惠券状态参数
     * @return 订阅优惠券状态
     */
    @PostMapping("/get/subscribe/status")
    public Result<Integer> getSubscribeStatus(@Valid @RequestBody VoucherSubscribeDto voucherSubscribeDto){
        return Result.ok(voucherService.getSubscribeStatus(voucherSubscribeDto));
    }

    /**
     * 批量获取订阅优惠券状态
     * @param voucherSubscribeBatchDto 批量获取订阅优惠券状态参数
     * @return 订阅优惠券状态
     */
    @PostMapping("/get/subscribe/status/batch")
    public Result<List<GetSubscribeStatusVo>> getSubscribeStatusBatch(@Valid @RequestBody VoucherSubscribeBatchDto voucherSubscribeBatchDto){
        return Result.ok(voucherService.getSubscribeStatusBatch(voucherSubscribeBatchDto));
    }
}
