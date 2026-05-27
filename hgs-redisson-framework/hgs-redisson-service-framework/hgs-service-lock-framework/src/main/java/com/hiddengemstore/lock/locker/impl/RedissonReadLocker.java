package com.hiddengemstore.lock.locker.impl;

import com.hiddengemstore.lock.locker.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 读锁实现
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class RedissonReadLocker implements ServiceLocker {

    private final RedissonClient redissonClient;


    @Override
    public RLock getLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey).readLock();
    }

    @Override
    public RLock lock(String lockKey) {
        RLock readLock = redissonClient.getReadWriteLock(lockKey).readLock();
        readLock.lock();
        return readLock;
    }

    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock readLock = redissonClient.getReadWriteLock(lockKey).readLock();
        readLock.lock(leaseTime, TimeUnit.SECONDS);
        return readLock;
    }

    @Override
    public RLock lock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        RLock readLock = redissonClient.getReadWriteLock(lockKey).readLock();
        readLock.lock(leaseTime, timeUnit);
        return readLock;
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit) {
        RLock readLock = redissonClient.getReadWriteLock(lockKey).readLock();
        try {
            return readLock.tryLock(waitTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock readLock = redissonClient.getReadWriteLock(lockKey).readLock();
        try {
            return readLock.tryLock(waitTime, leaseTime, timeUnit);
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
