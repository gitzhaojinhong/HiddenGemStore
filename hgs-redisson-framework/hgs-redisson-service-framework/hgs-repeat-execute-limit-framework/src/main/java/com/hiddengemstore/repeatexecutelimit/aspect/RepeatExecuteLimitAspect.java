package com.hiddengemstore.repeatexecutelimit.aspect;

import com.hiddengemstore.core.RedissonDataHandle;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.locallock.LocalLockCache;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.factory.ServiceLockFactory;
import com.hiddengemstore.lock.locker.ServiceLocker;
import com.hiddengemstore.lockinfo.LockInfoHandle;
import com.hiddengemstore.lockinfo.constants.LockInfoType;
import com.hiddengemstore.lockinfo.factory.LockInfoHandleFactory;
import com.hiddengemstore.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.hiddengemstore.repeatexecutelimit.constant.RepeatExecuteLimitConstant;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.hiddengemstore.repeatexecutelimit.constant.RepeatExecuteLimitConstant.PREFIX_NAME;
import static com.hiddengemstore.repeatexecutelimit.constant.RepeatExecuteLimitConstant.SUCCESS_FLAG;

/**
 * @author : ZhaoJH
 */
@Slf4j
@Aspect
@Order(-11)// 在业务加锁之前执行
@AllArgsConstructor
public class RepeatExecuteLimitAspect {
    private final LockInfoHandleFactory lockInfoHandleFactory;
    private final ServiceLockFactory serviceLockFactory;
    private final LocalLockCache localLockCache;
    private final RedissonDataHandle redissonDataHandle;

    @Around("@annotation(repeatExecuteLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatExecuteLimit) throws Throwable {
        // 步骤1: 获取注解配置的锁保持时间和提示信息
        long durationTime = repeatExecuteLimit.durationTime();
        String message = repeatExecuteLimit.message();

        // 步骤2: 根据注解参数动态生成唯一的锁名称：repeat_flaghgs-REPEAT_EXECUTE_LIMIT:createOrder:userId123、
        // lockName格式: {应用前缀}-REPEAT_EXECUTE_LIMIT:{name}:{key1}:{key2}
        // repeatFlagName格式: repeat_flag{应用前缀}-REPEAT_EXECUTE_LIMIT:{name}:{key1}:{key2}
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        String lockName = lockInfoHandle.getLockName(joinPoint, repeatExecuteLimit.name(), repeatExecuteLimit.keys());
        String repeatFlagName = PREFIX_NAME + lockName;

        // 步骤3: 第一次检查 - 快速失败，如果Redis中已存在成功标识则直接抛出异常
        Object obj;
        String flagObject = redissonDataHandle.get(repeatFlagName);
        if (SUCCESS_FLAG.equals(flagObject)) {
            throw new HGSFrameException(message);
        }

        // 步骤4: 获取本地锁(JVM级别)，减少分布式锁的竞争压力
        ReentrantLock localLock = localLockCache.getLock(lockName, true);
        boolean localLockResult = localLock.tryLock();
        if (!localLockResult) {
            throw new HGSFrameException(message);
        }
        try {
            // 步骤5: 获取分布式公平锁，确保集群环境下的互斥性
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Fair);
            boolean lockResult = lock.tryLock(lockName, 0, TimeUnit.SECONDS);

            if (lockResult) {
                try {
                    // 步骤6: 第二次检查 - 双重检查锁定模式(DCL)，防止并发问题
                    flagObject = redissonDataHandle.get(repeatFlagName);
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new HGSFrameException(message);
                    }

                    // 步骤7: 执行目标业务方法
                    obj = joinPoint.proceed();

                    // 步骤8: 业务执行成功后，在Redis中设置成功标识并设置过期时间
                    if (durationTime > 0) {
                        try {
                            redissonDataHandle.set(repeatFlagName, SUCCESS_FLAG, durationTime, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            log.error("getBucket error", e);
                        }
                    }
                    return obj;
                } finally {
                    // 步骤9: 释放分布式锁
                    lock.unlock(lockName);
                }
            } else {
                // 步骤10: 获取分布式锁失败，抛出异常
                throw new HGSFrameException(message);
            }
        } finally {
            // 步骤11: 释放本地锁
            localLock.unlock();
        }
    }
}
