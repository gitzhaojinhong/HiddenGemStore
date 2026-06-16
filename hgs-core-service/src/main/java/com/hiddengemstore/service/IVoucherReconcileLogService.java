package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.VoucherReconcileLog;
import com.hiddengemstore.entity.dto.VoucherReconcileLogDto;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.message.MessageExtend;

public interface IVoucherReconcileLogService extends IService<VoucherReconcileLog> {
    void saveReconcileLog(Integer logType,
                          Integer businessType,
                          String detail,
                          Long traceId,
                          MessageExtend<SeckillVoucherMessage> message);
    void saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto);

}
