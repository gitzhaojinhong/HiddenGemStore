package com.hiddengemstore.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.IAutoIssueNotifyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 自动发券成功后的用户通知服务接口实现
 * @author : ZhaoJH
 */
@Slf4j
@Service
public class AutoIssueNotifyServiceImpl implements IAutoIssueNotifyService {

    // 是否开启短信通知，默认关闭
    @Value("${seckill.autoissue.notify.sms.enabled:false}")
    private boolean smsEnabled;

    // 是否开启APP内通知，默认关闭
    @Value("${seckill.autoissue.notify.app.enabled:false}")
    private boolean appEnabled;

    // 短信接收号码，为空则不发送
    @Value("${seckill.autoissue.notify.sms.to:}")
    private String smsTo;

    // 去重窗口时间（秒），同一用户同一券在窗口内只通知一次
    @Value("${seckill.autoissue.notify.dedup.window.seconds:300}")
    private long dedupWindowSeconds;

    @Resource
    private RedisCache redisCache;

    /**
     * 发送自动发券通知：先去重，再根据配置的渠道（短信/APP）发送通知。
     * 整体 try-catch 包裹，保证通知失败不影响主业务流程。
     */
    @Override
    public void sendAutoIssueNotify(Long voucherId, Long userId, Long orderId) {
        try {
            // 去重检查：窗口期内同一用户同一券不重复通知
            if (!shouldNotify(voucherId, userId)) {
                return;
            }
            // 组装通知内容
            String content = String.format("自动发券成功 | voucherId=%s userId=%s orderId=%s", voucherId, userId, orderId);
            // 短信通知渠道：开关开启且号码非空时发送
            if (smsEnabled && StrUtil.isNotBlank(smsTo)) {
                log.info("[AUTOISSUE_SMS] to={} content={}", smsTo, content);
            }
            // APP内通知渠道：开关开启时发送
            if (appEnabled) {
                log.info("[AUTOISSUE_APP] userId={} content={}", userId, content);
            }
        } catch (Exception e) {
            log.warn("发送自动发券通知异常", e);
        }
    }

    /**
     * 去重判断：利用 Redis SETNX 实现滑动窗口去重。
     * key 不存在则写入并返回 true（允许通知），已存在则返回 false（跳过）。
     * Redis 异常时降级放行（返回 true），宁可多通知也不漏通知。
     */
    private boolean shouldNotify(Long voucherId, Long userId) {
        try {
            return redisCache.setIfAbsent(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_AUTO_ISSUE_NOTIFY_DEDUP_KEY, voucherId, userId),
                    "1",
                    dedupWindowSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            // Redis 异常降级放行，避免因缓存故障导致通知丢失
            return true;
        }
    }
}
