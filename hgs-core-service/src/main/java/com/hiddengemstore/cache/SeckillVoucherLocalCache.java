package com.hiddengemstore.cache;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.hiddengemstore.entity.SeckillVoucher;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存：秒杀优惠券详情
 *
 * @author : ZhaoJH
 */
@Component
public class SeckillVoucherLocalCache {
    private final Cache<String, SeckillVoucherFullModel> cache = Caffeine.newBuilder()
            .maximumSize(10000)
            // 自定义过期策略
            .expireAfter(new Expiry<String, SeckillVoucherFullModel>() {
                // 创建时过期时间
                @Override
                public long expireAfterCreate(String key, SeckillVoucherFullModel value, long currentTime) {
                    // 默认60秒
                    long ttlSecondes = 60;
                    // 动态计算：优惠卷结束时间到现在的秒数，保底为1秒
                    if (value != null && value.getEndTime() != null) {
                        ttlSecondes = Math.max(
                                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), value.getEndTime()).getSeconds()
                                , 1
                        );
                    }
                    // 转换为纳秒返回
                    return TimeUnit.NANOSECONDS.convert(ttlSecondes,TimeUnit.SECONDS);
                }
                // 更新时过期时间
                @Override
                public long expireAfterUpdate(String key, SeckillVoucherFullModel value, long currentTime, long currentDuration) {
                    // 保持原来的过期时间
                    return currentDuration;
                }
                // 读取时过期时间
                @Override
                public long expireAfterRead(String key, SeckillVoucherFullModel value, long currentTime, long currentDuration) {
                    // 保持原来的过期时间
                    return currentDuration;
                }
            }).build();

    /**
     * 根据优惠券ID查询本地缓存，不存在则返回null
     */
    public SeckillVoucherFullModel get(String voucherId){
        return cache.getIfPresent(voucherId);
    }

    /**
     * 将秒杀优惠券信息放入本地缓存，key和value均不为null时才执行
     */
    public void put(String voucherId,SeckillVoucherFullModel seckillVoucher){
        if (voucherId!=null&&seckillVoucher!=null) {
            cache.put(voucherId,seckillVoucher);
        }
    }

    /**
     * 根据优惠券ID主动失效本地缓存，用于数据变更后保持一致性
     */
    public void invalidate(String voucherId){
        cache.invalidate(voucherId);
    }
}
