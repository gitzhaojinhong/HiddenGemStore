package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.VoucherReconcileLog;
import com.hiddengemstore.mapper.VoucherReconcileLogMapper;
import com.hiddengemstore.service.IVoucherReconcileLogService;
import org.springframework.stereotype.Service;

@Service
public class VoucherReconcileLogServiceImpl extends ServiceImpl<VoucherReconcileLogMapper, VoucherReconcileLog> implements IVoucherReconcileLogService {
}
