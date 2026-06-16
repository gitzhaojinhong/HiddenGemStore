package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.entity.VoucherReconcileLog;
import com.hiddengemstore.entity.dto.VoucherReconcileLogDto;
import com.hiddengemstore.enums.LogType;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.mapper.VoucherReconcileLogMapper;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.service.IVoucherReconcileLogService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherReconcileLogServiceImpl extends ServiceImpl<VoucherReconcileLogMapper, VoucherReconcileLog> implements IVoucherReconcileLogService {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReconcileLog(Integer logType, Integer businessType, String detail, Long traceId, MessageExtend<SeckillVoucherMessage> message) {
        SeckillVoucherMessage messageBody = message.getMessageBody();
        VoucherReconcileLogDto voucherReconcileLogDto = new VoucherReconcileLogDto()
                .setOrderId(messageBody.getOrderId())
                .setUserId(messageBody.getUserId())
                .setVoucherId(messageBody.getVoucherId())
                .setMessageId(message.getUuid())
                .setDetail(detail)
                .setBeforeQty(messageBody.getBeforeQty())
                .setChangeQty(messageBody.getChangeQty())
                .setAfterQty(messageBody.getAfterQty())
                .setTraceId(traceId)
                .setLogType(logType)
                .setBusinessType(businessType);
        // 如果类型RESTORE恢复库存，则对调 扣减后库存数量 和 扣减前库存数量
        if (voucherReconcileLogDto.getLogType().equals(LogType.RESTORE.getCode())) {
            voucherReconcileLogDto.setBeforeQty(messageBody.getAfterQty());
            voucherReconcileLogDto.setAfterQty(messageBody.getBeforeQty());
        }
        saveReconcileLog(voucherReconcileLogDto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto) {
        VoucherReconcileLog logEntity = new VoucherReconcileLog();
        logEntity.setId(snowflakeIdGenerator.nextId())
                .setOrderId(voucherReconcileLogDto.getOrderId())
                .setUserId(voucherReconcileLogDto.getUserId())
                .setVoucherId(voucherReconcileLogDto.getVoucherId())
                .setMessageId(voucherReconcileLogDto.getMessageId())
                .setBusinessType(voucherReconcileLogDto.getBusinessType())
                .setDetail(voucherReconcileLogDto.getDetail())
                .setTraceId(voucherReconcileLogDto.getTraceId())
                .setLogType(voucherReconcileLogDto.getLogType())
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setBeforeQty(voucherReconcileLogDto.getBeforeQty())
                .setChangeQty(voucherReconcileLogDto.getChangeQty())
                .setAfterQty(voucherReconcileLogDto.getAfterQty());
        save(logEntity);
    }
}
