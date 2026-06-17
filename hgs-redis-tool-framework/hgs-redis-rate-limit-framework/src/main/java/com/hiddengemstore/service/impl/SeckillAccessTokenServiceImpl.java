package com.hiddengemstore.service.impl;

import cn.hutool.core.util.IdUtil;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.lua.SeckillAccessTokenOperate;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.ISeckillAccessTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.hiddengemstore.redis.api.RedisCache;

import java.util.concurrent.TimeUnit;


/**
 * @author : ZhaoJH
 */
@Slf4j
@Service
public class SeckillAccessTokenServiceImpl implements ISeckillAccessTokenService {
    @Value("${seckill.access.token.enabled:true}")
    private boolean enabled;

    @Value("${seckill.access.token.ttl-seconds:30}")
    private long ttlSeconds;

    @Resource
    private RedisCache redisCache;

    @Resource
    private MeterRegistry meterRegistry;

    private SeckillAccessTokenOperate operate;

    @PostConstruct
    public void init() {
        operate = new SeckillAccessTokenOperate(redisCache);
    }


    /**
     * 判断访问令牌功能是否启用
     * 从配置文件中读取seckill.access.token.enabled配置项,控制令牌机制的开关
     * @return true表示启用令牌验证,false表示禁用
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 为用户颁发访问令牌(accessToken)
     * 使用UUID生成唯一令牌,通过SETNX原子操作保证同一用户同一券只能获取一个有效令牌
     * @param voucherId 秒杀券ID,用于构建Redis key的维度之一
     * @param userId 用户ID,用于构建Redis key的维度之一
     * @return 访问令牌字符串,如果已存在则返回已有令牌,否则返回新生成的令牌
     */
    @Override
    public String issueAccessToken(Long voucherId, Long userId) {
        // 生成简单格式的UUID作为访问令牌
        String token = IdUtil.simpleUUID();
        // 使用SETNX原子操作设置令牌,保证幂等性(key不存在时才设置成功)
        boolean ok = redisCache.setIfAbsent(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ACCESS_TOKEN_TAG_KEY, voucherId, userId),
                token,
                ttlSeconds,
                TimeUnit.SECONDS);
        // 如果SETNX失败,说明该用户已存在有效令牌,需要处理冲突
        if (!ok) {
            // 查询已存在的令牌值
            String existing = redisCache.get(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ACCESS_TOKEN_TAG_KEY, voucherId, userId),
                    String.class);
            // 记录令牌颁发冲突指标,便于监控重复请求
            safeInc("seckill_access_token_issue_conflict", "component", "service_impl");
            // 优先返回已存在的令牌,如果查询失败则返回新生成的令牌(降级策略)
            return existing != null ? existing : token;
        }
        // 记录令牌颁发成功指标
        safeInc("seckill_access_token_issue_success", "component", "service_impl");
        log.info("获取到令牌成功！令牌：{}", token);
        return token;
    }


    /**
     * 验证并消费访问令牌
     * 通过Lua脚本原子性地校验令牌有效性并删除已使用的令牌,防止重放攻击
     * @param voucherId 秒杀券ID,用于定位Redis中的令牌key
     * @param userId 用户ID,用于定位Redis中的令牌key
     * @param token 待验证的访问令牌字符串
     * @return true表示令牌有效且已成功消费,false表示令牌无效或已被使用
     */
    @Override
    public boolean validateAndConsume(Long voucherId, Long userId, String token) {
        // 构建访问令牌的Redis key
        String key = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ACCESS_TOKEN_TAG_KEY, voucherId, userId).getRelKey();
        // 执行Lua脚本原子性验证并消费令牌(校验+删除)
        boolean success = operate.validateAndConsume(key, token);
        // 记录令牌消费结果指标(成功/失败)
        safeInc(success ? "seckill_access_token_consume_success" : "seckill_access_token_consume_fail",
                "component", "service_impl");
        return success;
    }


    /**
     * 安全地递增监控指标计数器
     * 使用try-catch保护指标上报逻辑,避免因监控异常影响主业务流程
     * @param name 指标名称,用于标识不同的业务事件
     * @param tagKey 标签键,用于指标的维度分类
     * @param tagValue 标签值,具体的维度取值
     */
    @SuppressWarnings("SameParameterValue")
    private void safeInc(String name, String tagKey, String tagValue) {
        try {
            // 检查MeterRegistry是否可用,避免空指针异常
            if (meterRegistry != null) {
                // 创建或获取计数器并递增
                meterRegistry.counter(name, tagKey, tagValue).increment();
            }
        } catch (Exception ignore) {
            // 静默忽略监控指标异常,确保不影响核心业务逻辑
        }
    }

}
