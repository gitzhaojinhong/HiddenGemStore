package com.hiddengemstore.executor;

import com.hiddengemstore.lockinfo.constants.LockInfoType;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.factory.ServiceLockFactory;
import com.hiddengemstore.lock.locker.ServiceLocker;
import com.hiddengemstore.lock.timeout.LockTimeOutStrategy;
import com.hiddengemstore.lockinfo.LockInfoHandle;
import com.hiddengemstore.lockinfo.factory.LockInfoHandleFactory;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 提供命令模式和方法级别的加锁 API
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class ServiceLockExecutor {
    private final LockInfoHandleFactory lockInfoHandleFactory;
    private final ServiceLockFactory serviceLockFactory;

    /**
     * 没有返回值的加锁执行
     * @param taskRun 要执行的任务
     * @param name 锁的业务名
     * @param keys 锁的标识
     */
    public void execute(TaskRun taskRun,String name,String [] keys) {
        execute(taskRun,name,keys,20);
    }

    /**
     * 没有返回值的加锁执行
     * @param taskRun 要执行的任务
     * @param name 锁的业务名
     * @param keys 锁的标识
     * @param waitTime 等待时间
     */
    public void execute(TaskRun taskRun,String name,String [] keys,long waitTime){
        execute(LockType.Reentrant,taskRun,name,keys,waitTime);
    }

    /**
     * 没有返回值的加锁执行
     * @param lockType 锁类型
     * @param taskRun 要执行的任务
     * @param name 锁的业务名
     * @param keys 锁的标识
     */
    public void execute(LockType lockType, TaskRun taskRun, String name, String [] keys) {
        execute(lockType,taskRun,name,keys,20);
    }
    /**
     * 没有返回值的加锁执行
     * @param lockType 锁类型
     * @param taskRun 要执行的任务
     * @param name 锁的业务名
     * @param keys 锁的标识
     * @param waitTime 等待时间
     */
    private void execute(LockType lockType, TaskRun taskRun, String name, String[] keys, long waitTime) {
        // 1. 获取锁名解析处理器
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        // 2. 生成锁名（简单拼接方式，不走 SpEL）
        String lockName = lockInfoHandle.simpleGetLockName(name, keys);
        // 3. 获取锁实例
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        // 4. 尝试加锁
        boolean result = lock.tryLock(lockName, waitTime, TimeUnit.SECONDS);
        if (result) {
            try {
                // 5. 执行业务逻辑
                taskRun.run();
            } finally {
                // 6. 解锁
                lock.unlock(lockName);
            }
        }else {
            // 5. 加锁失败，快速失败
            LockTimeOutStrategy.FAIL.handler(lockName);
        }
    }
    /**
     * 有返回值的加锁执行
     * @param taskCall 要执行的任务
     * @param name 锁的业务名
     * @param keys 锁的标识
     * @return 要执行的任务的返回值
     * */
    public <T> T submit(TaskCall<T> taskCall,String name,String [] keys){
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name,keys);
        ServiceLocker lock = serviceLockFactory.getLock(LockType.Reentrant);
        boolean result = lock.tryLock(lockName, 30, TimeUnit.SECONDS);
        if (result) {
            try {
                return taskCall.call();
            }finally {
                lock.unlock(lockName);
            }
        }else {
            LockTimeOutStrategy.FAIL.handler(lockName);
        }
        return null;
    }

    /**
     * 获得锁
     * @param lockType 锁类型
     * @param name 锁的业务名
     * @param keys 锁的标识
     *
     * */
    public RLock getLock(LockType lockType, String name, String [] keys) {
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        String lockName = lockInfoHandle.simpleGetLockName(name,keys);
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        return lock.getLock(lockName);
    }

    /**
     * 获得锁
     * @param lockType 锁类型
     * @param lockName 锁名
     *
     * */
    public RLock getLock(LockType lockType, String lockName) {
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        return lock.getLock(lockName);
    }

}
