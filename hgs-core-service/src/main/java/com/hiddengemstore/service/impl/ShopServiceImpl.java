package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.Shop;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.executor.ServiceLockExecutor;
import com.hiddengemstore.factory.BloomFilterHandlerFactory;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.mapper.ShopMapper;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.IShopService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hiddengemstore.Constants.RedisConstants.CACHE_SHOP_TTL;
import static com.hiddengemstore.Constants.RedisConstants.LOCK_SHOP_KEY;
import static com.hiddengemstore.constant.Constant.BLOOM_FILTER_HANDLER_SHOP;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private RedisCache redisCache;
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;
    @Resource
    private ServiceLockExecutor serviceLockExecutor;


    @Override
    public Result<Shop> queryById(Long id) {
        // 第1步：Redis缓存查询
        Shop shop = redisCache.get(
                RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id),
                Shop.class);
        // 如果缓存中存在就直接返回
        if (Objects.nonNull(shop)) {
            return Result.ok(shop);
        }
        log.info("查询商铺 从Redis缓存没有查询到 商铺id : {}", id);
        // 第2步：布隆过滤器判断是否存在
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_SHOP)
                .contains(String.valueOf(id))) {
            log.info("查询商铺 布隆过滤器判断不存在 商铺id : {}", id);
            throw new RuntimeException("查询商铺不存在");
        }
        // 第3步：检查空值缓存（解决缓存穿透）
        Boolean existResult = redisCache.hasKey(
                RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id));
        if (existResult){
            throw new RuntimeException("查询商铺不存在");
        }
        // 第4步：获取分布式锁（解决缓存击穿）
        RLock lock = serviceLockExecutor.getLock(
                LockType.Reentrant,
                LOCK_SHOP_KEY,
                new String[]{String.valueOf(id)});
        lock.lock();
        try {
            // 第5步：双重检测 - 再次检查空值缓存
            existResult = redisCache.hasKey(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id));
            if (existResult){
                throw new RuntimeException("查询商铺不存在");
            }
            // 第5步：双重检测 - 再次从缓存中获取商铺信息，通过此步骤可以避免大量请求在获取锁后，直接击穿缓存访问数据库
            shop = redisCache.get(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id),
                    Shop.class);
            // 如果缓存中存在就直接返回
            if (Objects.nonNull(shop)) {
                return Result.ok(shop);
            }
            // 第6步：查询数据库
            shop = getById(id);
            // 第7步：处理查询结果
            if (Objects.isNull(shop)) {
                // 写入空值缓存，防止缓存穿透
                redisCache.set(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id),
                        "空值",
                        CACHE_SHOP_TTL,
                        TimeUnit.MINUTES);
                throw new RuntimeException("查询商铺不存在");
            }
            // 如果数据库查询不是空的，将商铺信息写入缓存
            redisCache.set(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id),
                    shop,
                    CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);
            return Result.ok(shop);
        } finally {
            // 第8步：释放锁
            lock.unlock();
        }
    }
}
