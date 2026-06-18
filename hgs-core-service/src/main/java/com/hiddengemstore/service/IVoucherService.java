package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.Voucher;
import com.hiddengemstore.entity.dto.*;
import com.hiddengemstore.entity.vo.GetSubscribeStatusVo;
import jakarta.validation.Valid;

import java.util.List;

public interface IVoucherService extends IService<Voucher> {
    void updateSeckillVoucher(@Valid UpdateSeckillVoucherDto updateSeckillVoucherDto);

    void subscribe(@Valid VoucherSubscribeDto voucherSubscribeDto);

    void unsubscribe(@Valid VoucherSubscribeDto voucherSubscribeDto);

    Integer getSubscribeStatus(@Valid VoucherSubscribeDto voucherSubscribeDto);

    List<GetSubscribeStatusVo> getSubscribeStatusBatch(@Valid VoucherSubscribeBatchDto voucherSubscribeBatchDto);

    void updateSeckillVoucherStock(@Valid UpdateSeckillVoucherStockDto updateSeckillVoucherDto);

    Long addSeckillVoucher(@Valid SeckillVoucherDto seckillVoucherDto);

    Long addVoucher(@Valid VoucherDto voucherDto);
}
