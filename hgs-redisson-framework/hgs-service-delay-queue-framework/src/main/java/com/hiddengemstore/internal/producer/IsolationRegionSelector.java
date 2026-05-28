package com.hiddengemstore.internal.producer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分区选择器
 * 用于延迟队列中分区索引的循环选择，通过原子计数器实现轮询算法。
 * 当计数器达到阈值时自动重置，确保索引在有效范围内循环。
 * @author ZhaoJH
 */
public class IsolationRegionSelector {
    /**
     * 当前计数值
     */
    private final AtomicInteger count = new AtomicInteger(0);
        
    /**
     * 分区数量阈值
     */
    private final Integer thresholdValue;

    /**
     * 构造隔离区选择器
     * @param thresholdValue 分区数量阈值，超过此值后计数器重置
     */
    public IsolationRegionSelector(Integer thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    /**
     * 重置计数器并返回旧值
     * 使用原子操作 getAndSet 保证线程安全，将计数器重置为 0 并返回重置前的值。
     * @return 重置前的计数值
     */
    private int reset() {
        return count.getAndSet(0);
    }

    /**
     * 获取下一个分区索引
     * 采用轮询策略选择分区
     * 通过 synchronized 保证多线程环境下的安全性。
     * @return 当前选中的分区索引（从 0 开始）
     */
    public synchronized int getIndex(){
        int cur = count.get();
        if (cur >= thresholdValue) {
            cur = reset();
        }else {
            count.incrementAndGet();
        }
        return cur;
    }
}
