package com.hiddengemstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * redis配置
 * @author : ZhaoJH
 */
public class RedisFrameWorkAutoConfig {
    @Bean("redisToolRedisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // 设置key的默认序列化方式
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());
        // 连接工厂注入到 RedisTemplate 中
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }
    @Bean("redisToolStringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        // 设置key的默认序列化方式
        stringRedisTemplate.setDefaultSerializer(new StringRedisSerializer());
        // 连接工厂注入到 RedisTemplate 中
        stringRedisTemplate.setConnectionFactory(redisConnectionFactory);
        return stringRedisTemplate;
    }

}
