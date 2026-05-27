package com.hiddengemstore.core;

import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 提供简洁的redisson操作API
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class RedissonDataHandle {
    private final RedissonClient redissonClient;
    /**
     * 获取字符串缓存数据
     * @param key 缓存key
     * @return 字符串类型的缓存值，不存在返回null
     */
    public String get(String key){
        return (String)redissonClient.getBucket(key).get();
    }

    /**
     * 设置字符串缓存数据（永不过期）
     * @param key 缓存key
     * @param value 缓存数据
     */
    public void set(String key,String value){
        redissonClient.getBucket(key).set(value);
    }

    /**
     * 带过期时间设置缓存数据
     * @param key 缓存key
     * @param value 缓存数据
     * @param timeToLive 过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key,String value,long timeToLive, TimeUnit timeUnit){
        redissonClient.getBucket(key).set(value,getDuration(timeToLive,timeUnit));
    }

    /**
     * 获取Duration
     * @param timeToLive 过期时间
     * @param timeUnit 时间单位
     * @return Duration对象
     */
    public Duration getDuration(long timeToLive, TimeUnit timeUnit){
        switch (timeUnit) {
            case MINUTES -> {
                return Duration.ofMinutes(timeToLive);
            }
            case HOURS -> {
                return Duration.ofHours(timeToLive);
            }
            case DAYS -> {
                return Duration.ofDays(timeToLive);
            }
            default -> {
                return Duration.ofSeconds(timeToLive);
            }
        }
    }
}
