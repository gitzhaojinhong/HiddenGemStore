package com.hiddengemstore.extension.penalty;

import com.hiddengemstore.config.SeckillRateLimitConfigProperties;
import com.hiddengemstore.context.RateLimitContext;
import com.hiddengemstore.enums.BaseCode;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于阈值的临时封禁
 * @author : ZhaoJH
 */
@Slf4j
@RequiredArgsConstructor//自动注入final字段
public class ThresholdPenaltyPolicy implements RateLimitPenaltyPolicy{

    private final RedisCache redisCache;
    private final SeckillRateLimitConfigProperties props;
    @Override
    public void apply(RateLimitContext context, BaseCode reason) {
        try {
            if (reason == BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED) {
                applyForIp(context);
            } else if (reason == BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED) {
                applyForUser(context);
            }
        } catch (Exception e) {
            log.debug("Penalty policy apply failed: {}", e.getMessage());
        }
    }

    /**
     * 对IP维度应用惩罚策略
     * 记录IP违规次数,当累计违规次数达到阈值时将其加入黑名单进行封禁
     * @param ctx 限流上下文,包含券ID、客户端IP等关键信息
     */
    private void applyForIp(RateLimitContext ctx) {
        // 从限流上下文中提取券ID和客户端IP
        Long voucherId = ctx.getVoucherId();
        String clientIp = ctx.getClientIp();
        // 如果券ID或客户端IP为空,则跳过惩罚逻辑
        if (Objects.isNull(voucherId) || Objects.isNull(clientIp)) {
            return;
        }
        // 构建IP违规计数器Redis key,用于累加该IP的违规次数
        RedisKeyBuild violationKey = RedisKeyBuild.createRedisKey(
                RedisKeyManage.SECKILL_VIOLATION_IP_TAG_KEY, voucherId, clientIp);
        // 原子递增违规计数,返回递增后的值
        long count = redisCache.incrBy(violationKey, 1L);
        // 如果是首次违规(count=1),设置违规计数器的过期时间(滑动窗口)
        if (count == 1L) {
            redisCache.expire(violationKey, props.getViolationWindowSeconds(), TimeUnit.SECONDS);
        }
        // 当违规次数达到IP封禁阈值时,将IP加入黑名单进行临时封禁
        if (count >= props.getIpBlockThreshold()) {
            // 构建IP黑名单Redis key,标记该IP已被封禁
            RedisKeyBuild blockKey = RedisKeyBuild.createRedisKey(
                    RedisKeyManage.SECKILL_BLOCK_IP_TAG_KEY, voucherId, clientIp);
            // 设置黑名单key及过期时间,实现临时封禁效果
            redisCache.set(blockKey, "1", props.getIpBlockTtlSeconds(), TimeUnit.SECONDS);
            // 记录警告日志,便于监控和排查异常IP
            log.warn("Temporary banned IP: voucherId={}, ip={}, ttlSeconds={}, violationCount={}",
                    voucherId, clientIp, props.getIpBlockTtlSeconds(), count);
        }
    }
    /**
     * 对用户维度应用惩罚策略
     * 记录用户违规次数,当累计违规次数达到阈值时将其加入黑名单进行封禁
     * @param ctx 限流上下文,包含券ID、用户ID等关键信息
     */
    private void applyForUser(RateLimitContext ctx) {
        // 从限流上下文中提取券ID和用户ID
        Long voucherId = ctx.getVoucherId();
        Long userId = ctx.getUserId();
        // 如果券ID或用户ID为空,则跳过惩罚逻辑
        if (Objects.isNull(voucherId) || Objects.isNull(userId)) {
            return;
        }
        // 构建用户违规计数器Redis key,用于累加该用户的违规次数
        RedisKeyBuild violationKey = RedisKeyBuild.createRedisKey(
                RedisKeyManage.SECKILL_VIOLATION_USER_TAG_KEY, voucherId, userId);
        // 原子递增违规计数,返回递增后的值
        long count = redisCache.incrBy(violationKey, 1L);
        // 如果是首次违规(count=1),设置违规计数器的过期时间(滑动窗口)
        if (count == 1L) {
            redisCache.expire(violationKey, props.getViolationWindowSeconds(), TimeUnit.SECONDS);
        }
        // 当违规次数达到用户封禁阈值时,将用户加入黑名单进行临时封禁
        if (count >= props.getUserBlockThreshold()) {
            // 构建用户黑名单Redis key,标记该用户已被封禁
            RedisKeyBuild blockKey = RedisKeyBuild.createRedisKey(
                    RedisKeyManage.SECKILL_BLOCK_USER_TAG_KEY, voucherId, userId);
            // 设置黑名单key及过期时间,实现临时封禁效果
            redisCache.set(blockKey, "1", props.getUserBlockTtlSeconds(), TimeUnit.SECONDS);
            // 记录警告日志,便于监控和排查异常用户行为
            log.warn("Temporary banned user: voucherId={}, userId={}, ttlSeconds={}, violationCount={}",
                    voucherId, userId, props.getUserBlockTtlSeconds(), count);
        }
    }
}
