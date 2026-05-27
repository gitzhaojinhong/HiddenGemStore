package com.hiddengemstore.lockinfo;

import org.aspectj.lang.JoinPoint;

/**
 * 锁信息抽象
 * @author : ZhaoJH
 */
public interface LockInfoHandle {
    /**
     * 用于获取AOP注解使用的锁名称
     * @param joinPoint 切点
     * @param lockName 锁名称
     * @param keys 锁的key
     * @return 锁名称
     */
    String getLockName(JoinPoint joinPoint, String lockName,String[] keys);

    /**
     * 用于获取编程式调用的锁名称
     * @param lockName 锁名称
     * @param keys 锁的key
     * @return 锁名称
     */
    String simpleGetLockName(String lockName,String[] keys);
}
