package com.hiddengemstore.execute;

import com.hiddengemstore.config.SeckillRateLimitConfigProperties;
import com.hiddengemstore.context.RateLimitContext;
import com.hiddengemstore.enums.BaseCode;
import com.hiddengemstore.enums.RateLimitScene;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.extension.Listener.RateLimitEventListener;
import com.hiddengemstore.extension.penalty.RateLimitPenaltyPolicy;
import com.hiddengemstore.lua.SlidingRateLimitOperate;
import com.hiddengemstore.lua.TokenBucketRateLimitOperate;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 限流执行实现
 * @author : ZhaoJH
 */
@RequiredArgsConstructor//自动注入final字段
public class RedisRateLimitHandler implements RateLimitHandler{

    private final SeckillRateLimitConfigProperties seckillRateLimitConfigProperties;
    private final RedisCache redisCache;
    private final SlidingRateLimitOperate slidingRateLimitOperate;
    private final TokenBucketRateLimitOperate tokenBucketRateLimitOperate;
    private final RateLimitEventListener rateLimitEventListener;
    private final RateLimitPenaltyPolicy rateLimitPenaltyPolicy;


    @Override
    public void execute(Long voucherId,
                        Long userId,
                        RateLimitScene scene) {
        // 获取客户端IP
        String clientIp = resolveClientIp();
        // 验证白名单
        if (isWhitelisted(userId, clientIp)) {
            return;
        }
        //验证黑名单
        checkBans(voucherId, userId, clientIp);
        //IP限流窗口毫秒数
        int ipLimitWindowMillis = resolveIpWindow(scene);
        //IP最大尝试次数
        int ipLimitMaxAttempts = resolveIpMaxAttempts(scene);
        //用户限流窗口毫秒数
        int userLimitWindowMillis = resolveUserWindow(scene);
        //用户最大尝试次数
        int userLimitMaxAttempts = resolveUserMaxAttempts(scene);
        //是否启用滑动窗口限流，默认false，采用动态令牌
        boolean useSliding = resolveSliding();
        //构建lua中的键名
        List<String> keys = buildRateLimitKeys(voucherId, userId, clientIp, useSliding);
        //构建lua中的数据
        String[] args = buildArgs(ipLimitWindowMillis, ipLimitMaxAttempts, userLimitWindowMillis, userLimitMaxAttempts);

        RateLimitContext ctx = buildContext(voucherId, userId, clientIp, keys, useSliding,
                ipLimitWindowMillis, ipLimitMaxAttempts, userLimitWindowMillis, userLimitMaxAttempts);
        //执行前置
        safeBeforeExecute(ctx);
        //在lua中执行滑动窗口或者令牌的限流功能
        Integer result = executeLua(useSliding, keys, args);
        ctx.setResult(result);
        //验证是否触发限流
        handleResult(ctx);
    }
    /**
     * 处理限流结果
     */
    private void handleResult(RateLimitContext ctx) {
        Integer result = ctx.getResult();
        //成功
        if (BaseCode.SUCCESS.getCode().equals(result)) {
            rateLimitEventListener.onAllowed(ctx);
            return;
        }
        //IP限流
        if (BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED.getCode().equals(result)) {
            rateLimitEventListener.onBlocked(ctx, BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED);
            rateLimitPenaltyPolicy.apply(ctx, BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED);
            throw new HGSFrameException(BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED);
        }
        //用户限流
        if (BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED.getCode().equals(result)) {
            rateLimitEventListener.onBlocked(ctx, BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED);
            rateLimitPenaltyPolicy.apply(ctx, BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED);
            throw new HGSFrameException(BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED);
        }
        throw new HGSFrameException("操作频繁，请稍后再试");
    }
    /**
     * 执行Lua限流
     * @param useSliding 是否为滑动窗口限流
     * @param keys 键名
     * @param args 参数
     * @return 执行结果
     */
    private Integer executeLua(boolean useSliding, List<String> keys, String[] args) {
        return useSliding
                ?
                slidingRateLimitOperate.execute(keys, args)
                :
                tokenBucketRateLimitOperate.execute(keys, args);
    }
    /**
     * 前置处理
     */
    private void safeBeforeExecute(RateLimitContext ctx) {
        rateLimitEventListener.onBeforeExecute(ctx);
    }
    /**
     * 构建限流上下文
     */
    private RateLimitContext buildContext(Long voucherId,
                                          Long userId,
                                          String clientIp,
                                          List<String> keys,
                                          boolean useSliding,
                                          int ipWindowMillis, int ipMaxAttempts,
                                          int userWindowMillis, int userMaxAttempts) {
        return new RateLimitContext(
                voucherId,
                userId,
                clientIp,
                keys,
                useSliding,
                ipWindowMillis,
                ipMaxAttempts,
                userWindowMillis,
                userMaxAttempts
        );
    }

    /**
     * 构建lua脚本执行参数数组
     * 将限流配置参数转换为字符串数组,用于传递给Redis Lua脚本执行
     * @param ipWindowMillis IP维度限流窗口时间(毫秒),控制IP请求频率的时间窗口
     * @param ipMaxAttempts IP维度最大尝试次数,窗口时间内允许的最大请求数
     * @param userWindowMillis 用户维度限流窗口时间(毫秒),控制用户请求频率的时间窗口
     * @param userMaxAttempts 用户维度最大尝试次数,窗口时间内允许的最大请求数
     * @return Lua脚本参数字符串数组,按[ip窗口,ip次数,用户窗口,用户次数]顺序排列
     */
    private String[] buildArgs(int ipWindowMillis,
                               int ipMaxAttempts,
                               int userWindowMillis,
                               int userMaxAttempts) {
        String[] args = new String[4];
        args[0] = String.valueOf(ipWindowMillis);
        args[1] = String.valueOf(ipMaxAttempts);
        args[2] = String.valueOf(userWindowMillis);
        args[3] = String.valueOf(userMaxAttempts);
        return args;
    }

    /**
     * 构建lua脚本键名
     * @param voucherId 秒杀ID
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @param useSliding 是否为滑动窗口限流
     * @return 键名
     */
    private List<String> buildRateLimitKeys(Long voucherId,
                                            Long userId,
                                            String clientIp,
                                            boolean useSliding) {
        // 初始化限流key列表,最多包含IP限流和用户限流两个key
        List<String> keys = new ArrayList<>(2);
        // 如果客户端IP有效,则构建IP维度的限流key(防止同一IP高频请求)
        if (Objects.nonNull(clientIp)) {
            // 根据滑动窗口或令牌桶算法选择对应的Redis key模板
            String ipKey = useSliding
                    ? RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_IP_SW_TAG_KEY, voucherId, clientIp).getRelKey()
                    : RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_IP_TB_TAG_KEY, voucherId, clientIp).getRelKey();
            keys.add(ipKey);
        }
        // 构建用户维度的限流key(防止同一用户高频请求)
        String userKey = useSliding
                ? RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_USER_SW_TAG_KEY, voucherId, userId).getRelKey()
                : RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_USER_TB_TAG_KEY, voucherId, userId).getRelKey();
        keys.add(userKey);
        return keys;
    }

    /**
     * 是否启用滑动窗口限流
     * @return 是否启用滑动窗口限流
     */
    private boolean resolveSliding() {
        return seckillRateLimitConfigProperties.getEnableSlidingWindow();
    }

    /**
     * 获取用户限流窗口毫秒数
     * @param scene 限流场景
     * @return 用户限流窗口毫秒数
     */
    private int resolveUserWindow(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN
                        ?
                        seckillRateLimitConfigProperties.getIssue()
                        :
                        seckillRateLimitConfigProperties.getSeckill();

        Integer v =
                ep != null
                        ?
                        ep.getUserWindowMillis()
                        :
                        null;

        return v != null
                ?
                v
                :
                seckillRateLimitConfigProperties.getUserWindowMillis();
    }
    /**
     * 获取用户最大尝试次数
     * @param scene 限流场景
     * @return 用户最大尝试次数
     */
    private int resolveUserMaxAttempts(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN
                        ?
                        seckillRateLimitConfigProperties.getIssue()
                        :
                        seckillRateLimitConfigProperties.getSeckill();

        Integer v =
                ep != null
                        ?
                        ep.getUserMaxAttempts()
                        :
                        null;

        return v != null
                ?
                v
                :
                seckillRateLimitConfigProperties.getUserMaxAttempts();
    }
    /**
     * 获取IP最大尝试次数
     * @param scene 限流场景
     * @return IP最大尝试次数
     */
    private int resolveIpMaxAttempts(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN
                        ?
                        seckillRateLimitConfigProperties.getIssue()
                        :
                        seckillRateLimitConfigProperties.getSeckill();

        Integer v =
                ep != null
                        ?
                        ep.getIpMaxAttempts()
                        :
                        null;
        return v != null
                ?
                v
                :
                seckillRateLimitConfigProperties.getIpMaxAttempts();
    }

    /**
     * 获取IP限流窗口毫秒数
     * @param scene 限流场景
     * @return 限流窗口毫秒数
     */
    private int resolveIpWindow(RateLimitScene scene) {
        // 根据限流场景获取对应的限流窗口毫秒数
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN
                        ?
                        seckillRateLimitConfigProperties.getIssue()
                        :
                        seckillRateLimitConfigProperties.getSeckill();
        // 取出限流窗口毫秒数
        Integer v =
                ep != null
                        ?
                        ep.getIpWindowMillis()
                        :
                        null;
        return v != null
                ?
                v
                :
                seckillRateLimitConfigProperties.getIpWindowMillis();

    }

    /**
     * 验证黑名单
     * @param voucherId 秒杀卷ID
     * @param userId 用户ID
     * @param clientIp 客户端IP
     */
    private void checkBans(Long voucherId,
                           Long userId,
                           String clientIp) {
        // 验证IP黑名单
        if (Objects.nonNull(clientIp)) {
            boolean ipBlocked = Boolean.TRUE.equals(redisCache.hasKey(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_BLOCK_IP_TAG_KEY, voucherId, clientIp)));
            if (ipBlocked) {
                throw new HGSFrameException(BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED);
            }
        }
        // 验证用户黑名单
        boolean userBlocked = Boolean.TRUE.equals(redisCache.hasKey(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_BLOCK_USER_TAG_KEY, voucherId, userId)));
        if (userBlocked) {
            throw new HGSFrameException(BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED);
        }
    }

    /**
     * 判断是否在白名单中
     * @param userId 用户ID
     * @param clientIp 客户端IP
     * @return 是否在白名单中
     */
    private boolean isWhitelisted(Long userId, String clientIp) {
        try {
            return (clientIp != null && seckillRateLimitConfigProperties.getIpWhitelist() != null
                    && seckillRateLimitConfigProperties.getIpWhitelist().contains(clientIp))
                    || (userId != null && seckillRateLimitConfigProperties.getUserWhitelist() != null
                    && seckillRateLimitConfigProperties.getUserWhitelist().contains(userId));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取客户端IP
     * @return 客户端IP
     */
    private String resolveClientIp() {
        try {
            // 从请求上下文中获取当前HTTP请求属性
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            // 获取HttpServletRequest对象用于提取客户端IP
            HttpServletRequest request = attrs.getRequest();
            // 优先从X-Forwarded-For头获取真实IP(适用于经过代理/负载均衡的场景)
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null&&!xff.isEmpty()) {
                // XFF可能包含多个IP,格式为"client, proxy1, proxy2",取第一个为真实客户端IP
                String[] parts = xff.split(",");
                if (parts.length>0) {
                    String ip = parts[0].trim();
                    if (!ip.isEmpty()) {
                        return ip;
                    }
                }
            }
            // 其次尝试从X-Real-IP头获取真实IP(Nginx等反向代理常用)
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null&&!realIp.isEmpty()) {
                return realIp;
            }
            // 降级方案:直接使用请求的远程地址(可能是代理服务器IP)
            return request.getRemoteAddr();
        } catch (Exception e) {
            // 异常情况下返回null,避免影响限流逻辑的正常执行
            return null;
        }
    }
}
