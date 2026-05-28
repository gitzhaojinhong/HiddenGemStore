package com.hiddengemstore.api.producer;

import com.hiddengemstore.config.DelayQueueProperties;
import com.hiddengemstore.internal.producer.DelayQueueProduceCombine;
import org.redisson.api.RedissonClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 生产者入口
 * @author : ZhaoJH
 */
public class DelayQueueProducer {
    // RedissonClient实例
    private final RedissonClient redissonClient;
    // 延迟队列配置信息
    private final DelayQueueProperties delayQueueProperties;
    /**
     * key为topic主题，value为发送消息的处理器
     * 使用线程安全的Map
     * */
    private final Map<String, DelayQueueProduceCombine> delayQueueProduceCombineMap = new ConcurrentHashMap<>();

    public DelayQueueProducer(RedissonClient redissonClient, DelayQueueProperties delayQueueProperties) {
        this.redissonClient = redissonClient;
        this.delayQueueProperties = delayQueueProperties;
    }

    /**
     * 发送延迟消息
     * @param topic 主题
     * @param content 消息内容
     * @param delayTime 延迟时间
     * @param timeUnit 时间单位
     */
    public void sendMessage(String topic, String content, long delayTime, TimeUnit timeUnit){
        // 获取延迟队列处理器，computeIfAbsent方法——>懒初始化，第一次调用且不存在时，调用指定的函数创建对象并返回
        DelayQueueProduceCombine delayQueueProduceCombine = delayQueueProduceCombineMap.computeIfAbsent(topic,
                k -> new DelayQueueProduceCombine(redissonClient, delayQueueProperties, topic));
        // 发送延迟消息
        delayQueueProduceCombine.offer(content,delayTime,timeUnit);
    }
}
