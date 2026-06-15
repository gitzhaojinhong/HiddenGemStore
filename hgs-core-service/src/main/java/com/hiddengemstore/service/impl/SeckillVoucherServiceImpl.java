package com.hiddengemstore.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.cache.SeckillVoucherLocalCache;
import com.hiddengemstore.constant.Constant;
import com.hiddengemstore.entity.SeckillVoucher;
import com.hiddengemstore.entity.Voucher;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.executor.ServiceLockExecutor;
import com.hiddengemstore.factory.BloomFilterHandlerFactory;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.annotation.ServiceLock;
import com.hiddengemstore.mapper.SeckillVoucherMapper;
import com.hiddengemstore.mapper.VoucherMapper;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.ISeckillVoucherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hiddengemstore.Constants.RedisConstants.*;
import static com.hiddengemstore.constant.Constant.BLOOM_FILTER_HANDLER_VOUCHER;
import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_LOCK;
import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_STOCK_LOCK;

@Slf4j
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
    @Resource
    private ServiceLockExecutor serviceLockExecutor;

    @Resource
    private RedisCache redisCache;

    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;

    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private VoucherMapper voucherMapper;


    /**
     * 查询秒杀券完整信息（多级缓存+防穿透+防击穿），该方案空值仅缓存在Redis中
     * 1. 查询本地Caffeine缓存，命中直接返回
     * 2. 查询Redis缓存，命中则写入本地缓存后返回
     * 3. 布隆过滤器判断ID是否存在，不存在则抛异常
     * 4. 检查Redis空值缓存，存在则抛异常（防穿透）
     * 5. 加分布式读锁（防击穿）
     * 6. 双重检查：再次查询本地缓存、Redis缓存和空值缓存
     * 7. 查询数据库，不存在则缓存空值并抛异常
     * 8. 存在则组装数据，设置TTL为剩余有效时间，写入Redis和本地缓存
     * 9. 释放锁
     * 加读写锁原因：
     *  因为秒杀优惠券的更新操作（如改库存、改状态）很频繁，且缓存可能是直接更新而非删除。
     *  如果查询和更新并发，可能出现：查询线程读到旧数据 → 更新线程写入新缓存 → 查询线程又把旧数据覆盖回去。读写锁保证了读-读并发、读-写互斥。
     */
    @Override
    @ServiceLock(lockType = LockType.Read,name = UPDATE_SECKILL_VOUCHER_LOCK,keys = "#voucherId")
    public SeckillVoucherFullModel queryByVoucherId(Long voucherId) {
        // 构建Redis Key
        RedisKeyBuild seckillVoucherRedisKey = RedisKeyBuild
                .createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        // 构建空值缓存Redis Key
        RedisKeyBuild nullRedisKey = RedisKeyBuild
                .createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId);

        // 获取本地Caffeine缓存，命中返回
        SeckillVoucherFullModel localCacheHit = seckillVoucherLocalCache.get(seckillVoucherRedisKey.getRelKey());
        if (Objects.nonNull(localCacheHit)) {
            return localCacheHit;
        }
        // 获取Redis缓存，命中返回
        SeckillVoucherFullModel seckillVoucherFullModel = redisCache.get(seckillVoucherRedisKey, SeckillVoucherFullModel.class);
        if (Objects.nonNull(seckillVoucherFullModel)) {
            // 命中则存入本地缓存
            seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
            return seckillVoucherFullModel;
        }
        // 布隆滤器判断
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_VOUCHER).contains(String.valueOf(voucherId))) {
            throw new RuntimeException("秒杀券不存在");
        }
        // 空值判断，解决缓存穿透
        Boolean existResult = redisCache.hasKey(nullRedisKey);
        if (existResult) {
            throw new RuntimeException("秒杀券不存在");
        }
        // 加锁,解决缓存击穿
        RLock lock = serviceLockExecutor.getLock(LockType.Reentrant, LOCK_SECKILL_VOUCHER_KEY, new String[]{String.valueOf(voucherId)});
        lock.lock();
        try {
            // 双重判断本地缓存、Redis缓存和null值缓存
            localCacheHit = seckillVoucherLocalCache.get(seckillVoucherRedisKey.getRelKey());
            if (Objects.nonNull(localCacheHit)) {
                return localCacheHit;
            }
            seckillVoucherFullModel = redisCache.get(seckillVoucherRedisKey, SeckillVoucherFullModel.class);
            if (Objects.nonNull(seckillVoucherFullModel)) {
                // 命中则存入本地缓存,加快后续访问
                seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
                return seckillVoucherFullModel;
            }
            existResult = redisCache.hasKey(nullRedisKey);
            if (existResult) {
                throw new RuntimeException("秒杀券不存在");
            }
            // 缓存不存在，查询数据库
            SeckillVoucher seckillVoucher = lambdaQuery().eq(SeckillVoucher::getVoucherId, voucherId).one();
            if (Objects.isNull(seckillVoucher)) {
                // 缓存空值
                redisCache.set(nullRedisKey, "空值",CACHE_NULL_TTL, TimeUnit.MINUTES);
                throw new RuntimeException("秒杀券不存在");
            }
            long ttlSeconds = Math.max(
                    LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).getSeconds(),
                    1
            );
            Voucher voucher = voucherMapper.selectById(voucherId);
            seckillVoucherFullModel = new SeckillVoucherFullModel();
            BeanUtil.copyProperties(seckillVoucher, seckillVoucherFullModel);
            seckillVoucherFullModel.setShopId(voucher.getShopId())
                    .setStatus(voucher.getStatus())
                    .setStock(null);//不返回库存信息
            // 写到缓存中
            redisCache.set(seckillVoucherRedisKey, seckillVoucherFullModel, ttlSeconds, TimeUnit.SECONDS);
            seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
            return seckillVoucherFullModel;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @ServiceLock(lockType= LockType.Read,name = UPDATE_SECKILL_VOUCHER_STOCK_LOCK,keys = {"#voucherId"})
    public void loadVoucherStock(Long voucherId) {
        // 步骤1：布隆过滤器校验优惠券是否存在
        if (!bloomFilterHandlerFactory.get(Constant.BLOOM_FILTER_HANDLER_VOUCHER).contains(String.valueOf(voucherId))) {
            log.info("加载库存 布隆过滤器判断不存在 秒杀优惠券id : {}",voucherId);
            throw new RuntimeException("查询秒杀优惠券不存在");
        }
        
        // 步骤2：首次检查Redis缓存，避免重复加载
        String stock = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId), String.class);
        if (Objects.nonNull(stock)) {
            return;
        }
        
        // 步骤3：获取分布式锁，防止并发加载
        RLock lock = serviceLockExecutor.getLock(LockType.Reentrant, LOCK_SECKILL_VOUCHER_STOCK_KEY, new String[]{String.valueOf(voucherId)});
        lock.lock();
        try {
            // 步骤4：双重检查，防止重复加载
            stock = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId), String.class);
            if (Objects.nonNull(stock)) {
                return;
            }
            
            // 步骤5：从数据库查询优惠券信息并加载到Redis
            SeckillVoucher seckillVoucher = lambdaQuery().eq(SeckillVoucher::getVoucherId, voucherId).one();
            if (Objects.isNull(seckillVoucher)) {
                long ttlSeconds = Math.max(
                        LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).getSeconds(),
                        1L
                );
                redisCache.set(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId),
                        String.valueOf(seckillVoucher.getStock()),
                        ttlSeconds,
                        TimeUnit.SECONDS
                );
            }
        }finally {
            lock.unlock();
        }
    }
}
