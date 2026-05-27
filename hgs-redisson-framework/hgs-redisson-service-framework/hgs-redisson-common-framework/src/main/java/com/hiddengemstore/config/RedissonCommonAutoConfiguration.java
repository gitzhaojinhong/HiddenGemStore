package com.hiddengemstore.config;

import com.hiddengemstore.core.RedissonDataHandle;
import com.hiddengemstore.locallock.LocalLockCache;
import com.hiddengemstore.lockinfo.factory.LockInfoHandleFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * redisson通用配置
 * @author : ZhaoJH
 */
//@AutoConfigureBefore 在 Redisson 官方自动配置之前执行，确保使用自定义配置
@AutoConfigureBefore(value = {RedissonAutoConfigurationV2.class, RedissonAutoConfiguration.class})// 引入redisson自动配置
@EnableConfigurationProperties(RedissonBaseProperties.class)// 引入redisson配置
public class RedissonCommonAutoConfiguration {

    /**
     * 用于原子操作生成线程名称的唯一序号
     * 注意：虽然此Bean只创建一次，但线程工厂会在运行时被多次并发调用
     * 使用AtomicInteger确保并发创建线程时序号不重复
     */
    private final AtomicInteger executeTaskThreadCount = new AtomicInteger(1);

    /**
     * 创建RedissonClient Bean（项目启动时只执行一次）
     * 返回的RedissonClient是单例，在整个应用生命周期中复用
     */
    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties, RedissonBaseProperties redissonBaseProperties){
        // 创建config对象
        Config config = new Config();
        // 判断是否启用SSL集群
        String prefix = "redis://";
        Method method = ReflectionUtils.findMethod(RedisProperties.class, "isSsl");
        if (method != null && (Boolean)ReflectionUtils.invokeMethod(method, redisProperties)) {
            prefix = "rediss://";
        }
        // 配置单机模式：地址、超时、数据库、密码
        config.useSingleServer()
                .setAddress(prefix + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setConnectTimeout(1000)
                .setDatabase(redisProperties.getDatabase())
                .setPassword(redisProperties.getPassword());
        // 设置基础线程数
        config.setThreads(redissonBaseProperties.getThreads());
        config.setNettyThreads(redissonBaseProperties.getNettyThreads());
        // 如果指定了核心/最大线程数，创建自定义线程池
        if (Objects.nonNull(redissonBaseProperties.getCorePoolSize()) &&
                Objects.nonNull(redissonBaseProperties.getMaximumPoolSize())) {
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                    redissonBaseProperties.getCorePoolSize(),
                    redissonBaseProperties.getMaximumPoolSize(),
                    redissonBaseProperties.getKeepAliveTime(),
                    redissonBaseProperties.getUnit(),
                    new LinkedBlockingQueue<>(redissonBaseProperties.getWorkQueueSize()),
                    // 线程工厂：线程池需要新线程时才会调用（运行时并发执行）
                    // 每次调用getAndIncrement()都会原子性地返回当前值并自增，确保线程名唯一
                    r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                            "redisson-thread-" + executeTaskThreadCount.getAndIncrement()));
            config.setExecutor(threadPoolExecutor);
        }
        return Redisson.create(config);
    }

    /**
     * 创建RedissonDataHandle对象，用于操作Redisson数据
     */
    @Bean
    public RedissonDataHandle redissonDataHandle(RedissonClient redissonClient){
        return new RedissonDataHandle(redissonClient);
    }

    /**
     * 创建本地锁缓存对象，用于缓存本地锁信息
     */
    @Bean
    public LocalLockCache localLockCache(){
        return new LocalLockCache();
    }

    /**
     * 创建锁信息工厂对象，用于获取锁信息处理对象
     */
    @Bean
    public LockInfoHandleFactory lockInfoHandleFactory(){
        return new LockInfoHandleFactory();
    }
}
