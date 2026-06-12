package com.hiddengemstore.cache;

import com.hiddengemstore.context.SpringUtil;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.kafka.message.SeckillVoucherInvalidationMessage;
import com.hiddengemstore.kafka.producer.SeckillVoucherInvalidationProducer;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static com.hiddengemstore.constant.Constant.SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC;

/**
 * 业务发布入口：触发秒杀券缓存失效广播
 * @author : ZhaoJH
 */
@Component
public class SeckillVoucherCacheInvalidationPublisher {
    @Resource
    private RedisCache redisCache;
    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;
    // 秒杀券缓存失效生产者
    @Resource
    private SeckillVoucherInvalidationProducer seckillVoucherInvalidationProducer;

    /**
     * 触发指定券的缓存失效广播，并在当前实例立即清理
     * 作为发布消息的生产者，依赖调用方加锁，确保删除本地后删除Redis缓存的原子性
     * 【加写锁的核心作用】:
     *  为了防止"先清本地→后删Redis"之间的极短时间窗口（约1ms）内，
     *  业务读请求插入并读到Redis旧数据重载到本地。
     *  锁确保：清本地 → 删Redis → 业务读 严格串行化。
     * @param voucherId 券ID
     * @param reason 失效原因
     */
    public void publishInvalidate(Long voucherId,String reason){
        // 构建Redis Key
        RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        // 1) 当前实例先清理，缩短不一致窗口
        // 删除本地
        seckillVoucherLocalCache.invalidate(redisKey.getRelKey());
        // 删除Redis缓存
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId));// 删卷缓存
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId));// 删卷的库存缓存
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId));// 删空值缓存


        // 2) 广播Kafka消息到所有实例
        SeckillVoucherInvalidationMessage seckillVoucherInvalidationMessage = new SeckillVoucherInvalidationMessage(voucherId, reason);
        seckillVoucherInvalidationProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC,
                seckillVoucherInvalidationMessage
        );
    }

}
