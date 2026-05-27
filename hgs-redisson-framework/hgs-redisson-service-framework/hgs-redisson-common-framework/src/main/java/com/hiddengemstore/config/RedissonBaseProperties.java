package com.hiddengemstore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * redisson属性配置
 * @author : ZhaoJH
 */
@Data
@ConfigurationProperties(prefix = "spring.redis.redisson")
public class RedissonBaseProperties {
    /**
     * 线程池大小
     */
    private Integer threads = 16;

    /**
     * Netty线程池大小
     */
    private Integer nettyThreads = 32;

    /**
     * 核心线程数
     */
    private Integer corePoolSize = null;

    /**
     * 线程池最大线程数
     */
    private Integer maximumPoolSize = null;

    /**
     * 线程空闲时间
     */
    private long keepAliveTime = 30;

    /**
     * 线程空闲时间单位
     */
    private TimeUnit unit = TimeUnit.SECONDS;

    /**
     * 线程池任务队列大小
     */
    private Integer workQueueSize = 256;
}
