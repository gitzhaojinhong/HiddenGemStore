package com.hiddengemstore.lock.timeout;

/**
 * 锁超时处理策略接口
 * @author : ZhaoJH
 */
public interface LockTimeOutHandler {

    /**
     * 处理锁超时
     * @param lockName 锁名称
     */
    void handler(String lockName);
}
