package com.hiddengemstore.handler;

import com.hiddengemstore.lockinfo.AbstractLockInfoHandle;

/**
 * 分布式锁前缀统一命名
 * @author : ZhaoJH
 */
public class ServiceLockInfoHandle extends AbstractLockInfoHandle {
    /**
     * 锁的前缀名称
     */
    private static final String LOCK_PREFIX_NAME = "SERVICE_LOCK";
    /**
     * 获取锁前缀名称
     */
    @Override
    protected String getLockPrefixName() {
        return LOCK_PREFIX_NAME;
    }
}
