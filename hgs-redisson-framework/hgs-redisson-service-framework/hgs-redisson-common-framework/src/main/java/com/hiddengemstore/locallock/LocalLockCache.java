package com.hiddengemstore.locallock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地可重入锁缓存
 * 使用Caffeine缓存管理本地ReentrantLock实例，避免频繁创建锁对象
 * @author : ZhaoJH
 */
public class LocalLockCache {
    /**
     * Caffeine本地缓存，存储lockKey到ReentrantLock的映射
     * Key: 锁的唯一标识
     * Value: ReentrantLock实例（支持公平/非公平模式）
     */
    private Cache<String, ReentrantLock> localLockCache;
    
    /**
     * 锁缓存的过期时间（小时）
     * 默认48小时，可通过配置文件 durationTime 属性覆盖
     */
    @Value("${durationTime:48}")
    private Integer durationTime;
    
    /**
     * 初始化本地锁缓存
     * Spring容器启动时自动调用，构建Caffeine缓存实例
     * 配置策略：基于写入时间过期，超过durationTime小时未访问的锁将被清除
     * 注解PostConstruct : Spring生命周期，标记为初始化方法，属性填充后执行
     */
    @PostConstruct
    public void localLockCacheInit(){
        localLockCache = Caffeine.newBuilder()
                .expireAfterWrite(durationTime, TimeUnit.HOURS) // 设置过期策略
                .build();
    }
    
    /**
     * 获取或创建本地锁
     * 如果lockKey已存在，返回缓存中的锁；否则创建新锁并缓存
     * @param lockKey 锁的唯一标识
     * @param fair true=公平锁（FIFO），false=非公平锁（性能更高）
     * @return ReentrantLock实例，同一lockKey始终返回相同实例
     */
    public ReentrantLock getLock(String lockKey,boolean fair){
        return localLockCache.get(lockKey, key -> new ReentrantLock(fair));
    }
}
