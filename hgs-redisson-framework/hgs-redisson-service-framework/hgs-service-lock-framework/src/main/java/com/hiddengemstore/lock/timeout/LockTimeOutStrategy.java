package com.hiddengemstore.lock.timeout;

/**
 * 策略模式
 * 锁超时处理策略枚举
 * @author : ZhaoJH
 */
public enum LockTimeOutStrategy implements LockTimeOutHandler{
    /* 快速失败 */
    FAIL(){
        @Override
        public void handler(String lockName) {
            String msg = String.format("锁 %s 获取超时", lockName);
            throw new RuntimeException(msg);
        }
    }
}
