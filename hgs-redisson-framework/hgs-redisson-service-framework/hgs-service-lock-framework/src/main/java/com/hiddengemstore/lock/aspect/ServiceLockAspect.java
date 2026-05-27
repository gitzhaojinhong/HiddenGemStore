package com.hiddengemstore.lock.aspect;

import cn.hutool.core.util.StrUtil;
import com.hiddengemstore.constants.LockInfoType;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.annotation.ServiceLock;
import com.hiddengemstore.lock.factory.ServiceLockFactory;
import com.hiddengemstore.lock.locker.ServiceLocker;
import com.hiddengemstore.lockinfo.LockInfoHandle;
import com.hiddengemstore.lockinfo.factory.LockInfoHandleFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 锁切面
 * @author : ZhaoJH
 */
@Slf4j
@Aspect
@Order(-10)// 设置优先级比@Transactional高（先加锁，再开事务）
@AllArgsConstructor
public class ServiceLockAspect {

    private final LockInfoHandleFactory lockInfoHandleFactory;
    private final ServiceLockFactory serviceLockFactory;


    @Around("@annotation(serviceLock)")
    public Object around(ProceedingJoinPoint joinPoint,ServiceLock serviceLock) throws Throwable {
        // 1. 获取锁的名字解析处理器
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        // 2. 拼接锁的名字：{应用名}-SERVICE_LOCK:{name}:{key1}:{key2}
        String lockName = lockInfoHandle.getLockName(joinPoint, serviceLock.name(), serviceLock.keys());
        // 3. 从注解中获取配置参数
        LockType lockType = serviceLock.lockType();
        long waitTime = serviceLock.waitTime();
        TimeUnit timeUnit = serviceLock.timeUnit();
        // 4. 根据锁类型获取锁实例
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        // 5. 尝试加锁
        boolean result = lock.tryLock(lockName, waitTime, timeUnit);
        if (result) {
            try {
                // 6. 加锁成功，执行业务逻辑
                return joinPoint.proceed();
            } finally {
                // 7. 无论成功失败，都要解锁
                lock.unlock(lockName);
            }
        }else {
            // 6. 加锁失败
            log.warn("获取服务锁时暂停:{}",lockName);
            // 检查是否配置了自定义处理策略
            String customLockTimeoutStrategy = serviceLock.customLockTimeoutStrategy();
            if (StrUtil.isNotEmpty(customLockTimeoutStrategy)) {
                // 执行自定义处理策略
                return handleCustomLockTimeoutStrategy(customLockTimeoutStrategy,joinPoint);
            }else{
                // 执行默认处理策略（快速失败：抛异常）
                serviceLock.lockTimeOutStrategy().handler(lockName);
            }
            // 如果默认策略没有抛异常（理论上 FAIL 策略会抛异常），继续执行业务
            return joinPoint.proceed();
        }
    }
    /**
     * 处理自定义加锁超时策略
     * 通过反射调用当前类中与加锁方法参数相同的自定义方法
     */
    private Object handleCustomLockTimeoutStrategy(String customLockTimeoutStrategy, ProceedingJoinPoint joinPoint) {
        // 获取当前被拦截的方法
        Method currentMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Method handleMethod;
        try {
            // 通过反射获取自定义处理方法（方法名由注解的 customLockTimeoutStrategy 属性指定）
            handleMethod = target.getClass().getDeclaredMethod(customLockTimeoutStrategy, currentMethod.getParameterTypes());
            handleMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("注解参数 customLockTimeoutStrategy 不合法 :" + customLockTimeoutStrategy,e);
        }
        Object[] args = joinPoint.getArgs();
        // 调用自定义处理方法
        Object result;
        try {
            result = handleMethod.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法非法访问自定义锁超时处理程序: " + customLockTimeoutStrategy ,e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("调用自定义锁超时处理器失败: " + customLockTimeoutStrategy ,e);
        }
        return result;
    }
}
