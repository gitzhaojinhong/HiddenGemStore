package com.hiddengemstore.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.cache.SeckillVoucherCacheInvalidationPublisher;
import com.hiddengemstore.entity.SeckillVoucher;
import com.hiddengemstore.entity.Voucher;
import com.hiddengemstore.entity.dto.UpdateSeckillVoucherDto;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.annotation.ServiceLock;
import com.hiddengemstore.mapper.SeckillVoucherMapper;
import com.hiddengemstore.mapper.VoucherMapper;
import com.hiddengemstore.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_LOCK;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private SeckillVoucherCacheInvalidationPublisher seckillVoucherCacheInvalidationPublisher;


    @Override
    @ServiceLock(lockType = LockType.Write,name = UPDATE_SECKILL_VOUCHER_LOCK,keys = "#updateSeckillVoucherDto.voucherId")
    @Transactional(rollbackFor = Exception.class)// 开启事务 rollbackFor = Exception.class表示出现异常时回滚
    public void updateSeckillVoucher(UpdateSeckillVoucherDto updateSeckillVoucherDto) {
        Long voucherId = updateSeckillVoucherDto.getVoucherId();
        // 更新 tb_voucher 表的非空字段
        boolean voucherUpdatedStatus = false;
        LambdaUpdateChainWrapper<Voucher> voucherWrapper = this.lambdaUpdate().eq(Voucher::getId, voucherId);
        if (updateSeckillVoucherDto.getTitle() != null) {
            voucherWrapper.set(Voucher::getTitle, updateSeckillVoucherDto.getTitle());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getSubTitle() != null) {
            voucherWrapper.set(Voucher::getSubTitle, updateSeckillVoucherDto.getSubTitle());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getRules() != null) {
            voucherWrapper.set(Voucher::getRules, updateSeckillVoucherDto.getRules());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getPayValue() != null) {
            voucherWrapper.set(Voucher::getPayValue, updateSeckillVoucherDto.getPayValue());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getActualValue() != null) {
            voucherWrapper.set(Voucher::getActualValue, updateSeckillVoucherDto.getActualValue());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getType() != null) {
            voucherWrapper.set(Voucher::getType, updateSeckillVoucherDto.getType());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getStatus() != null) {
            voucherWrapper.set(Voucher::getStatus, updateSeckillVoucherDto.getStatus());
            voucherUpdatedStatus = true;
        }
        if (voucherUpdatedStatus) {
            voucherWrapper.set(Voucher::getUpdateTime, LocalDateTimeUtil.now());
        }
        // 更新 tb_seckill_voucher 表的非空字段（仅时间相关）
        boolean seckillVoucherUpdatedStatus = false;
        LambdaUpdateWrapper<SeckillVoucher> seckillWrapper = new LambdaUpdateWrapper<>();
        seckillWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        if (updateSeckillVoucherDto.getBeginTime() != null) {
            seckillWrapper.set(SeckillVoucher::getBeginTime, updateSeckillVoucherDto.getBeginTime());
            seckillVoucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getEndTime() != null) {
            seckillWrapper.set(SeckillVoucher::getEndTime, updateSeckillVoucherDto.getEndTime());
            seckillVoucherUpdatedStatus = true;
        }

        // 受众规则字段更新
        if (updateSeckillVoucherDto.getAllowedLevels() != null) {
            seckillWrapper.set(SeckillVoucher::getAllowedLevels, updateSeckillVoucherDto.getAllowedLevels());
            seckillVoucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getMinLevel() != null) {
            seckillWrapper.set(SeckillVoucher::getMinLevel, updateSeckillVoucherDto.getMinLevel());
            seckillVoucherUpdatedStatus = true;
        }
        if (seckillVoucherUpdatedStatus) {
            seckillWrapper.set(SeckillVoucher::getUpdateTime, LocalDateTimeUtil.now());
        }
        // 更新后清理缓存，等待读路径按新数据重建缓存
        if (voucherUpdatedStatus || seckillVoucherUpdatedStatus) {
            voucherWrapper.update();
            seckillVoucherMapper.update(seckillWrapper);
            seckillVoucherCacheInvalidationPublisher.publishInvalidate(voucherId,"update");
        }
    }
}
