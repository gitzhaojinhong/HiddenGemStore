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
     * 获取当前分区索引
     * 采用轮询策略选择分区
     * 通过 synchronized 保证多线程环境下的安全性。
     * 注意不要超出分区区间，如果区间为3，则索引为0、1、2
     * @return 当前选中的分区索引（从 0 开始）
     */
    public synchronized int getIndex(){
        int cur = count.get();
        if (cur >= thresholdValue) {
            // 计数器达到阈值，重置
            count.set(0);
            cur = 0;
        }else {
            count.incrementAndGet();
        }
        return cur;
    }
}
