package com.hiddengemstore.config;

import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.handler.WorkAndDataCenterIdHandler;
import com.hiddengemstore.model.WorkDataCenterId;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 在此说明，该模块属于框架模块，SpringBoot自动配置的最佳实践为imports+@Bean的方式管理Bean的生命周期
 * 自动装配雪花算法ID生成器
 * author : ZhaoJH
 */
public class IdGeneratorAutoConfig {
    /**
     * 自动装配工作节点和数据中心ID分配处理器，作为workDataCenterId方法的参数
     */
    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate){
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }

    /**
     * 从Redis获取并分配工作节点ID和数据中心ID，作为雪snowflakeIdGenerator方法的参数
     */
    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler){
        return workAndDataCenterIdHandler.getWorkAndDataCenterId();
    }

    /**
     * 自动装配雪花算法ID生成器
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId){
        return new SnowflakeIdGenerator(workDataCenterId);
    }
}
