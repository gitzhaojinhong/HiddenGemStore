package com.hiddengemstore.lock.manager;

import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.locker.ServiceLocker;
import com.hiddengemstore.lock.locker.impl.RedissonFairLocker;
import com.hiddengemstore.lock.locker.impl.RedissonReadLocker;
import com.hiddengemstore.lock.locker.impl.RedissonReentrantLocker;
import com.hiddengemstore.lock.locker.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.EnumMap;
import java.util.Map;

/**
 * 锁管理器
 * @author : ZhaoJH
 */
public class ManageLocker {
    private final Map<LockType, ServiceLocker> cacheLocker = new EnumMap<>(LockType.class);

    public ManageLocker(RedissonClient redissonClient) {
        cacheLocker.put(LockType.Reentrant, new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(LockType.Fair, new RedissonFairLocker(redissonClient));
        cacheLocker.put(LockType.Read, new RedissonReadLocker(redissonClient));
        cacheLocker.put(LockType.Write, new RedissonWriteLocker(redissonClient));
    }
    public ServiceLocker getReentrantLocker() {
        return cacheLocker.get(LockType.Reentrant);
    }
    public ServiceLocker getFairLocker() {
        return cacheLocker.get(LockType.Fair);
    }
    public ServiceLocker getReadLocker() {
        return cacheLocker.get(LockType.Read);
    }
    public ServiceLocker getWriteLocker() {
        return cacheLocker.get(LockType.Write);
    }
}
