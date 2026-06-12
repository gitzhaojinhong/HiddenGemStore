package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.Voucher;
import com.hiddengemstore.entity.dto.UpdateSeckillVoucherDto;
import jakarta.validation.Valid;

public interface IVoucherService extends IService<Voucher> {
    void updateSeckillVoucher(@Valid UpdateSeckillVoucherDto updateSeckillVoucherDto);
}
