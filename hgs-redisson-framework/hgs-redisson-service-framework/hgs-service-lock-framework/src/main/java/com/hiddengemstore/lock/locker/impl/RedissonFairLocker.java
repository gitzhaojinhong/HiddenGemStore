package com.hiddengemstore.lock.locker.impl;

import com.hiddengemstore.lock.locker.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 公平锁实现
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class RedissonFairLocker implements ServiceLocker {

    private final RedissonClient redissonClient;
    @Override
    public RLock getLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    @Override
    public RLock lock(String lockKey) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        fairLock.lock();
        return fairLock;
    }

    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        fairLock.lock(leaseTime, TimeUnit.SECONDS);
        return fairLock;
    }

    @Override
    public RLock lock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        fairLock.lock(leaseTime, timeUnit);
        return fairLock;
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        try {
            return fairLock.tryLock(waitTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        try {
            return fairLock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        // 判断当前线程是否持有锁,防止IllegalMonitorStateException异常
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
    @Override
    public void unlock(RLock lock) {
        lock.unlock();
    }
}
