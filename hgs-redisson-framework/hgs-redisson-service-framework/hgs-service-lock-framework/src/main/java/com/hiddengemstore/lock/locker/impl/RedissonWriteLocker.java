package com.hiddengemstore.lock.locker.impl;

import com.hiddengemstore.lock.locker.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 写锁实现
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class RedissonWriteLocker implements ServiceLocker {

    private final RedissonClient redissonClient;


    @Override
    public RLock getLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey).writeLock();
    }

    @Override
    public RLock lock(String lockKey) {
        RLock writeLock = redissonClient.getReadWriteLock(lockKey).writeLock();
        writeLock.lock();
        return writeLock;
    }

    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock writeLock = redissonClient.getReadWriteLock(lockKey).writeLock();
        writeLock.lock(leaseTime, TimeUnit.SECONDS);
        return writeLock;
    }

    @Override
    public RLock lock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        RLock writeLock = redissonClient.getReadWriteLock(lockKey).writeLock();
        writeLock.lock(leaseTime, timeUnit);
        return writeLock;
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit) {
        RLock writeLock = redissonClient.getReadWriteLock(lockKey).writeLock();
        try {
            return writeLock.tryLock(waitTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock writeLock = redissonClient.getReadWriteLock(lockKey).writeLock();
        try {
            return writeLock.tryLock(waitTime, leaseTime, timeUnit);
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
