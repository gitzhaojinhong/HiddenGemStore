package com.hiddengemstore.controller;

import com.hiddengemstore.entity.dto.GetSeckillVoucherDto;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.entity.dto.UpdateSeckillVoucherDto;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
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
     * 更新秒杀优惠券
     * @param updateSeckillVoucherDto 更新秒杀优惠券参数
     * @return 更新结果
     */
    @PostMapping("/update/seckill")
    public Result<Void> updateSeckillVoucher(@Valid @RequestBody UpdateSeckillVoucherDto updateSeckillVoucherDto){
        voucherService.updateSeckillVoucher(updateSeckillVoucherDto);
        return Result.ok();
    }
}
