package com.hiddengemstore.internal.consumer;

import com.hiddengemstore.api.consumer.DelayQueueConsumer;
import com.hiddengemstore.config.DelayQueueProperties;
import com.hiddengemstore.internal.base.DelayBaseQueue;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消费者实现
 *
 * @author : ZhaoJH
 */
@Slf4j
public class DelayConsumerQueue extends DelayBaseQueue {
    /**
     * 监听线程名称计数器
     */
    private final AtomicInteger listenStartThreadCount = new AtomicInteger(1);

    /**
     * 消费任务线程名称计数器
     */
    private final AtomicInteger executeTaskThreadCount = new AtomicInteger(1);

    /**
     * 队列监听线程池（单线程）
     */
    private final ThreadPoolExecutor listenStartThreadPool;

    /**
     * 消息消费业务线程池（异步执行）
     */
    private final ThreadPoolExecutor executeTaskThreadPool;

    /** 服务运行状态标记 */
    private final AtomicBoolean runFlag = new AtomicBoolean(false);

    /** 业务消费任务接口 */
    private final DelayQueueConsumer delayQueueConsumer;


    public DelayConsumerQueue(RedissonClient redissonClient, DelayQueueProperties properties,
                              DelayQueueConsumer consumer, String relTopic) {
        // 初始化RedissonClient实例和消息主题
        super(redissonClient, relTopic);
        // 初始化队列监听线程池（单线程）
        this.listenStartThreadPool = new ThreadPoolExecutor(
                1,              // 1. 核心线程数：始终保持存活的线程数量
                1,              // 2. 最大线程数：线程池最大能创建的线程总数
                60,             // 3. 非核心线程空闲存活时间
                TimeUnit.SECONDS,//4. 时间单位（秒）
                new LinkedBlockingQueue<>(), // 5. 任务等待队列
                r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        "listen-start-thread-" + listenStartThreadCount.getAndIncrement()) // 6. 线程工厂（命名线程）
        );
        // 初始化业务消费线程池（可配置核心线程、最大线程、队列大小）
        this.executeTaskThreadPool = new ThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getMaximumPoolSize(),
                properties.getKeepAliveTime(),
                properties.getUnit(),
                new LinkedBlockingQueue<>(properties.getWorkQueueSize()),
                r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        "delay-queue-consume-thread-" + executeTaskThreadCount.getAndIncrement()));
        this.delayQueueConsumer = consumer;
    }

    /**
     * 启动消费者线程
     */
    public synchronized void listenStart() {
        //如果runFlag为false，说明监听没有启动过
        if (!runFlag.get()){
            //将runFlag为true
            runFlag.set(true);
            // 持续监听队列
            listenStartThreadPool.execute(() -> {
                while (!Thread.interrupted()){
                    try {
                        // 断言、保证 blockingQueue 一定不是 null，如果是 null，程序直接崩溃报错。
                        assert blockingQueue != null;
                        // 阻塞获取消息
                        String content = blockingQueue.take();
                        // 异步消费
                        executeTaskThreadPool.execute(() -> {
                            try {
                                // 执行消费
                                delayQueueConsumer.execute(content);
                            } catch (Exception e){
                                log.error("消费失败",e);
                            }
                        });
                    } catch (InterruptedException e) {
                        // 线程中断，销毁线程池
                        // 先关 executeTaskThreadPool（不再接收新消费任务），再关 listenStartThreadPool（关闭监听线程池自身）
                        destroy(executeTaskThreadPool);
                        destroy(listenStartThreadPool);
                    } catch (Throwable e){
                        log.error("阻塞队列获取元素异常",e);
                    }
                }
            });
        }
    }

    private void destroy(ExecutorService executorService) {
        try {
            if (Objects.nonNull(executorService)) {
                executorService.shutdown();
            }
        } catch (Exception e) {
            log.error("销毁线程池异常", e);
        }
    }
}
