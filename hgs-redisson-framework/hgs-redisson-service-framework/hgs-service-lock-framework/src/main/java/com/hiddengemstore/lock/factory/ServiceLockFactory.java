package com.hiddengemstore.lock.factory;

import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.locker.ServiceLocker;
import com.hiddengemstore.lock.manager.ManageLocker;
import lombok.AllArgsConstructor;

/**
 * 锁工厂
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class ServiceLockFactory {
    private final ManageLocker manageLocker;

    public ServiceLocker getLock(LockType lockType) {
        return switch (lockType) {
            case Fair -> manageLocker.getFairLocker();
            case Write -> manageLocker.getWriteLocker();
            case Read -> manageLocker.getReadLocker();
            default -> manageLocker.getReentrantLocker();
        };
    }
}
