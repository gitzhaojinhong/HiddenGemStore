package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.Voucher;
import com.hiddengemstore.mapper.VoucherMapper;
import com.hiddengemstore.service.IVoucherService;
import org.springframework.stereotype.Service;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
}
