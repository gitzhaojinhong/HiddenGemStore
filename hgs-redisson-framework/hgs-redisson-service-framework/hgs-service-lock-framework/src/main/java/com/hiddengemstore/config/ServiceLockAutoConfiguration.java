package com.hiddengemstore.config;

import com.hiddengemstore.constants.LockInfoType;
import com.hiddengemstore.executor.ServiceLockExecutor;
import com.hiddengemstore.handler.ServiceLockInfoHandle;
import com.hiddengemstore.lock.aspect.ServiceLockAspect;
import com.hiddengemstore.lock.factory.ServiceLockFactory;
import com.hiddengemstore.lock.manager.ManageLocker;
import com.hiddengemstore.lockinfo.LockInfoHandle;
import com.hiddengemstore.lockinfo.factory.LockInfoHandleFactory;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
/**
 * 锁自动配置
 * @author : ZhaoJH
 */
public class ServiceLockAutoConfiguration {

    /**
     * 锁信息处理器
     * AOP中获取锁的名字解析处理器需要用到，所以需要提前注册
     */
    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
        return new ServiceLockInfoHandle();
    }

    /**
     * 锁管理器
     */
    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
        return new ManageLocker(redissonClient);
    }

    /**
     * 锁工厂
     */
    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
        return new ServiceLockFactory(manageLocker);
    }

    /**
     * 锁切面
     */
    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }

    /**
     * 提供命令模式和方法级别的加锁 API 的锁执行器
     */
    @Bean
    public ServiceLockExecutor serviceLockTooL(LockInfoHandleFactory lockInfoHandleFactory, ServiceLockFactory serviceLockFactory){
        return new ServiceLockExecutor(lockInfoHandleFactory,serviceLockFactory);
    }
}

