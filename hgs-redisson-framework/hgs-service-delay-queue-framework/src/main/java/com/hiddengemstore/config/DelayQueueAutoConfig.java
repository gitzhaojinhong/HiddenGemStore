package com.hiddengemstore.config;

import com.hiddengemstore.api.producer.DelayQueueProducer;
import com.hiddengemstore.internal.consumer.DelayQueueInitListener;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author : ZhaoJH
 */
@EnableConfigurationProperties(DelayQueueProperties.class)
public class DelayQueueAutoConfig {

    /**
     * 延迟队列初始化监听器
     */
    @Bean
    public DelayQueueInitListener delayQueueInitListener(RedissonClient redissonClient, DelayQueueProperties delayQueueProperties) {
        return new DelayQueueInitListener(redissonClient, delayQueueProperties);
    }

    /**
     * 延迟队列生产者
     */
    @Bean
    public DelayQueueProducer delayQueueProducer(RedissonClient redissonClient, DelayQueueProperties delayQueueProperties) {
        return new DelayQueueProducer(redissonClient, delayQueueProperties);
    }
}
