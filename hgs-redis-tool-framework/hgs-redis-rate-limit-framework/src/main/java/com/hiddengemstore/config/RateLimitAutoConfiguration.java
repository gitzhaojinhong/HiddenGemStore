package com.hiddengemstore.config;

import com.hiddengemstore.execute.RedisRateLimitHandler;
import com.hiddengemstore.extension.Listener.NoOpRateLimitEventListener;
import com.hiddengemstore.extension.Listener.RateLimitEventListener;
import com.hiddengemstore.extension.penalty.NoOpRateLimitPenaltyPolicy;
import com.hiddengemstore.extension.penalty.RateLimitPenaltyPolicy;
import com.hiddengemstore.extension.penalty.ThresholdPenaltyPolicy;
import com.hiddengemstore.lua.SlidingRateLimitOperate;
import com.hiddengemstore.lua.TokenBucketRateLimitOperate;
import com.hiddengemstore.redis.api.RedisCache;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 限流自动配置
 * @author : ZhaoJH
 */
@EnableConfigurationProperties(SeckillRateLimitConfigProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    public SlidingRateLimitOperate slidingRateLimitOperate(RedisCache redisCache){
        return new SlidingRateLimitOperate(redisCache);
    }

    @Bean
    public TokenBucketRateLimitOperate tokenBucketRateLimitOperate(RedisCache redisCache){
        return new TokenBucketRateLimitOperate(redisCache);
    }

    @Bean
    public RateLimitEventListener rateLimitEventListener(){
        return new NoOpRateLimitEventListener();
    }

    @Bean
    public RateLimitPenaltyPolicy rateLimitPenaltyPolicy(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                         RedisCache redisCache){

        Boolean enable = seckillRateLimitConfigProperties.getEnablePenalty();
        if (Boolean.TRUE.equals(enable)) {
            return new ThresholdPenaltyPolicy(redisCache, seckillRateLimitConfigProperties);
        }
        return new NoOpRateLimitPenaltyPolicy();
    }

    @Bean
    public RedisRateLimitHandler redisRateLimitHandler(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                       RedisCache redisCache,
                                                       SlidingRateLimitOperate slidingRateLimitOperate,
                                                       TokenBucketRateLimitOperate tokenBucketRateLimitOperate,
                                                       RateLimitEventListener rateLimitEventListener,
                                                       RateLimitPenaltyPolicy rateLimitPenaltyPolicy) {
        return new RedisRateLimitHandler(
                seckillRateLimitConfigProperties,
                redisCache,
                slidingRateLimitOperate,
                tokenBucketRateLimitOperate,
                rateLimitEventListener,
                rateLimitPenaltyPolicy
        );
    }
}
