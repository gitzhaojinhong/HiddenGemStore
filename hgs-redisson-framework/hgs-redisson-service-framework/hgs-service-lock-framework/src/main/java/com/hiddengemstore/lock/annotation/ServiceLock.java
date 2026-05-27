package com.hiddengemstore.lock.annotation;

import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.timeout.LockTimeOutStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author : ZhaoJH
 */
@Target(value = {ElementType.TYPE, ElementType.METHOD})// 注解作用在类和方法上
@Retention(value = RetentionPolicy.RUNTIME)// 注解保留在运行时
public @interface ServiceLock {
    /**
     * 锁类型: 默认可重入锁
     */
    LockType lockType() default LockType.Reentrant;
    /**
     * 锁的业务名称: 默认为空
     */
    String name() default "";
    /**
     * 锁的唯一标识（支持 SpEL 表达式）: 没有默认值，使用时必须指定
     */
    String[] keys();
    /**
     * 尝试加锁默认等待时间: 默认为10
     */
    long waitTime() default 10;
    /**
     * 尝试加锁默认等待时间单位: 默认为秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
    /**
     * 锁超时处理策略: 默认为快速失败
     */
    LockTimeOutStrategy lockTimeOutStrategy() default LockTimeOutStrategy.FAIL;
    /**
     * 自定义锁超时处理策略: 默认为空
     * 入参和出参需与加锁方法保持一致
     */
    String customLockTimeoutStrategy() default "";
}
