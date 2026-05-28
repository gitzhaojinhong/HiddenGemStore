package com.hiddengemstore.internal.producer;

import com.hiddengemstore.internal.base.DelayBaseQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 生产者实现
 * @author : ZhaoJH
 */
public class DelayProduceQueue extends DelayBaseQueue {

    // 社区版本提供的延迟队列，有消息丢失风险，已废弃，但可以使用消息对账功能来完善其可靠性
    @SuppressWarnings("deprecation")// 忽略弃用警告
    private final RDelayedQueue<String> delayedQueue;

    /**
     * 构造延迟队列生产者
     */
    public DelayProduceQueue(RedissonClient redissonClient, final String relTopic) {
        super(redissonClient,relTopic);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }

    /**
     * 消息发送到队列
     * @param content 消息
     * @param delayTime 延迟时间
     * @param timeUnit 时间单位
     */
    public void offer(String content, long delayTime, TimeUnit timeUnit){
        delayedQueue.offer(content,delayTime,timeUnit);
    }
}
