package com.hiddengemstore.lock;

/**
 * 锁类型枚举
 * @author : ZhaoJH
 */
public enum LockType {
    /* 可重入锁 */
    Reentrant,

    /* 公平锁 */
    Fair,

    /* 读锁 */
    Read,

    /* 写锁 */
    Write;

    LockType() {
    }
}
