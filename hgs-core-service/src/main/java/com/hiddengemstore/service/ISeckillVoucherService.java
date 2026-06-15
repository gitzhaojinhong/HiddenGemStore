package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.SeckillVoucher;
import com.hiddengemstore.entity.dto.GetSeckillVoucherDto;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
import jakarta.validation.Valid;

public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    SeckillVoucherFullModel queryByVoucherId(Long voucherId);

    void loadVoucherStock(Long voucherId);
}
