package com.hiddengemstore.internal.producer;

import com.hiddengemstore.config.DelayQueueProperties;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 生产者 分片选择
 * @author : ZhaoJH
 */
public class DelayQueueProduceCombine {
    // 分区选择器
    private final IsolationRegionSelector isolationRegionSelector;
    // 生产者列表
    private final List<DelayProduceQueue> delayProduceQueueList = new ArrayList<>();

    /**
     * 构造方法初始化列表中所有的生产者
     * @param redissonClient Redisson实例
     * @param delayQueueProperties 配置信息
     * @param topic 消息主题
     */
    public DelayQueueProduceCombine(RedissonClient redissonClient, DelayQueueProperties delayQueueProperties, String topic) {
        Integer isolationRegionCount = delayQueueProperties.getIsolationRegionCount();
        this.isolationRegionSelector = new IsolationRegionSelector(isolationRegionCount);
        for (int i = 0; i < isolationRegionCount; i++) {
            delayProduceQueueList.add(new DelayProduceQueue(redissonClient,topic + "-" + i));

        }
    }
    /**
     * 发送延迟消息
     * @param content 消息内容
     * @param delayTime 延迟时间
     * @param timeUnit 时间单位
     */
    public void offer(String content, long delayTime, TimeUnit timeUnit){
        int index = isolationRegionSelector.getIndex();
        delayProduceQueueList.get(index).offer(content,delayTime,timeUnit);
    }
}
