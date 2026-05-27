package com.hiddengemstore.lock.locker;

import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * 锁操作接口
 * @author : ZhaoJH
 */
public interface ServiceLocker {
    /**
     * 获取锁
     * @param lockKey 锁的Key
     * @return RLock
     */
    RLock getLock(String lockKey);

    /**
     * 加锁
     * @param lockKey 锁的Key
     * @return RLock
     */
    RLock lock(String lockKey);

    /**
     * 加锁，如果设置了leaseTime，看门狗机制失效
     * @param lockKey 锁的Key
     * @param leaseTime 锁的释放时间,默认单位:秒
     * @return RLock
     */
    RLock lock(String lockKey, long leaseTime);

    /**
     * 加锁，如果设置了leaseTime，看门狗机制失效
     * @param lockKey 锁的Key
     * @param leaseTime 锁的释放时间
     * @param timeUnit 时间单位
     * @return RLock
     */
    RLock lock(String lockKey, long leaseTime, TimeUnit timeUnit);

    /**
     * 尝试加锁
     * @param lockKey 锁的Key
     * @param waitTime 等待时间
     * @param timeUnit 时间单位
     * @return 是否获取锁成功
     */
    boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit);

    /**
     * 尝试加锁，如果设置了leaseTime，看门狗机制失效
     * @param lockKey 锁的Key
     * @param waitTime 等待时间
     * @param leaseTime 锁的释放时间
     * @param timeUnit 时间单位
     * @return 是否获取锁成功
     */
    boolean tryLock(String lockKey,long waitTime,long leaseTime,TimeUnit timeUnit);
    /**
     * 解锁
     * @param lockKey 锁的Key
     */
    void unlock(String lockKey);
    /**
     * 解锁
     * @param lock 锁
     */
    void unlock(RLock lock);
}
