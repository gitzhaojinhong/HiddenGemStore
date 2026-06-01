package com.hiddengemstore.config;

import com.hiddengemstore.factory.BloomFilterHandlerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 布隆过滤器自动配置类。
 * <br>
 * 通过 Spring Boot 的自动配置机制（AutoConfiguration.imports）自动加载。
 * 负责注册两个核心 Bean：
 * 1. BloomFilterHandlerFactory - 用于根据名称获取布隆过滤器处理器
 * 2. BloomFilterHandlerRegistrar - 根据配置动态注册多个布隆过滤器 Bean
 *
 * @author : ZhaoJH
 */
@EnableConfigurationProperties(BloomFilterProperties.class)
public class BloomFilterAutoConfiguration {
    @Bean
    public BloomFilterHandlerFactory bloomFilterHandlerFactory() {
        return new BloomFilterHandlerFactory();
    }
    @Bean
    public BloomFilterHandlerRegistrar bloomFilterHandlerRegistrar(Environment environment) {
        return new BloomFilterHandlerRegistrar(environment);
    }
}
