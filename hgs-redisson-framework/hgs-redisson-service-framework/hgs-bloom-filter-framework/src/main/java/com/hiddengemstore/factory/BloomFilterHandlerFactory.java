package com.hiddengemstore.factory;

import com.hiddengemstore.handler.BloomFilterHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 根据名称获取 BloomFilterHandler 的工厂
 * 实现 ApplicationContextAware，就是为了在普通类里，拿到 Spring 的容器（ApplicationContext），
 * 从而手动获取 Bean。
 * @author : ZhaoJH
 */
public class BloomFilterHandlerFactory implements ApplicationContextAware {
    // Spring 的容器
    private ApplicationContext applicationContext;

    /**
     * 根据名称获取 BloomFilterHandler Bean。
     * Bean的声明是在BloomFilterHandlerRegistrar.java中实现的
     * <br>
     * Bean 的名称是在配置文件中指定的，有两种方式：
     * 1. 使用配置中的 key 作为别名（如 "shop"、"voucher"）
     * 2. 使用 Filter.name 属性指定真正的 Bean 名称（如 "shop-id-bloom"）
     * <br>
     * 两种方式都可以用来获取 Bean：
     *   bloomFilterHandlerFactory.get("shop")          ← 使用配置 key（别名）
     *   bloomFilterHandlerFactory.get("shop-id-bloom") ← 使用 name 属性（Bean 名称）
     *
     * @param name BloomFilterHandler 的名称（配置 key 或 name 属性）
     * @return BloomFilterHandler
     */
    public BloomFilterHandler get(String name) {
        return applicationContext.getBean(name, BloomFilterHandler.class);
    }
    /**
     * 实现 ApplicationContextAware 接口，手动获取 Spring 的容器
     * @param applicationContext Spring 的容器
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
