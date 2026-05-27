package com.hiddengemstore.lockinfo.factory;

import com.hiddengemstore.lockinfo.LockInfoHandle;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/*
  读懂为什么存在这个类：
  假设你有多个 LockInfoHandle接口的实现：
    UserIdLockHandler - 基于用户ID生成锁key
    OrderIdLockHandler - 基于订单ID生成锁key
    ResourceIdLockHandler - 基于资源ID生成锁key
  工厂 + ApplicationContextAware = 运行时动态选择策略的实现基础，避免出现硬编码现象
 */

/**
 * 锁信息处理器工厂
 * 用于根据类型动态获取对应的LockInfoHandle实现
 * 支持策略模式，在运行时选择合适的锁名称生成策略
 * @author : ZhaoJH
 */
public class LockInfoHandleFactory implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * 根据类型获取对应的锁信息处理器
     * @param lockInfoType 处理器类型（Spring Bean名称）
     * @return 锁信息处理器实例
     */
    public LockInfoHandle getLockInfoHandle(String lockInfoType){
        return applicationContext.getBean(lockInfoType,LockInfoHandle.class);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
