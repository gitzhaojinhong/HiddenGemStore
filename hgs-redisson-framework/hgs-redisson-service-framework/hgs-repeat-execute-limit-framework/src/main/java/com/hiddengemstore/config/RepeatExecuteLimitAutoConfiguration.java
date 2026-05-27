package com.hiddengemstore.config;

import com.hiddengemstore.core.RedissonDataHandle;
import com.hiddengemstore.locallock.LocalLockCache;
import com.hiddengemstore.lock.factory.ServiceLockFactory;
import com.hiddengemstore.lockinfo.constants.LockInfoType;
import com.hiddengemstore.handle.RepeatExecuteLimitLockInfoHandle;
import com.hiddengemstore.lockinfo.LockInfoHandle;
import com.hiddengemstore.lockinfo.factory.LockInfoHandleFactory;
import com.hiddengemstore.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import org.springframework.context.annotation.Bean;

/**
 *
 * @author : ZhaoJH
 */
public class RepeatExecuteLimitAutoConfiguration {
    /**
     * 锁信息处理器，通过工厂指定bean名称获取
     */
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }

    /**
     * 注册幂等切面
     */
    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(lockInfoHandleFactory,serviceLockFactory,localLockCache,redissonDataHandle);
    }
}
