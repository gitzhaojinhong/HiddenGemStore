package com.hiddengemstore.handle;

import com.hiddengemstore.lockinfo.AbstractLockInfoHandle;

/**
 * 指定幂等锁的前缀名称
 * @author : ZhaoJH
 */
public class RepeatExecuteLimitLockInfoHandle extends AbstractLockInfoHandle {
    /**
     * "幂等"的前缀名称
     */
    public static final String PREFIX_NAME = "REPEAT_EXECUTE_LIMIT";

    /**
     * 获取锁的前缀名称
     */
    @Override
    protected String getLockPrefixName() {
        return PREFIX_NAME;
    }
}
