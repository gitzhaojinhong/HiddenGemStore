package com.hiddengemstore.redis.config;

import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisCacheImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * redis封装配置
 * @author : ZhaoJH
 */
public class RedisCacheAutoConfig {
    @Bean
    public RedisCache redisCache(@Qualifier("redisToolStringRedisTemplate") StringRedisTemplate stringRedisTemplate){
        return new RedisCacheImpl(stringRedisTemplate);
    }
}
