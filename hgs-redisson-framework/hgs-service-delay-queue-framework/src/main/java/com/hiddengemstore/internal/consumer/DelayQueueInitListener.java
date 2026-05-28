package com.hiddengemstore.internal.consumer;

import com.hiddengemstore.api.consumer.DelayQueueConsumer;
import com.hiddengemstore.config.DelayQueueProperties;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.CollectionUtils;

import java.util.Map;

/**
 * 延迟队列初始化监听器
 * ApplicationListener<ApplicationStartedEvent>: 监听ApplicationStartedEvent(项目启动完成事件)事件
 *
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class DelayQueueInitListener implements ApplicationListener<ApplicationStartedEvent> {
    // RedissonClient实例
    private final RedissonClient redissonClient;
    // 延迟队列配置信息
    private final DelayQueueProperties delayQueueProperties;

    @Override
    public void onApplicationEvent(@NonNull ApplicationStartedEvent event) {
        // 获取所有DelayQueueConsumer(消费者)实例
        Map<String, DelayQueueConsumer> consumerMap = event.getApplicationContext().getBeansOfType(DelayQueueConsumer.class);
        if (CollectionUtils.isEmpty(consumerMap)) {
            return;
        }
        // 获取分区数量
        Integer isolationRegionCount = delayQueueProperties.getIsolationRegionCount();
        for (DelayQueueConsumer consumer : consumerMap.values()) {
            // 启动消费者线程
            for (int i = 0; i < isolationRegionCount; i++) {
                DelayConsumerQueue delayConsumerQueue = new DelayConsumerQueue(
                        redissonClient, delayQueueProperties, consumer,
                        consumer.topic() + "-" + i);
                delayConsumerQueue.listenStart();
            }
        }
    }
}
