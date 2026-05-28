package com.hiddengemstore.internal.base;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;

/**
 * 公共基类
 * @author : ZhaoJH
 */
public class DelayBaseQueue {

    protected final RedissonClient redissonClient;
    // 阻塞队列：用于构造延迟队列
    protected final RBlockingQueue<String> blockingQueue;

    public DelayBaseQueue(RedissonClient redissonClient, String relTopic) {
        this.redissonClient = redissonClient;
        this.blockingQueue = redissonClient.getBlockingQueue(relTopic);
    }
}
