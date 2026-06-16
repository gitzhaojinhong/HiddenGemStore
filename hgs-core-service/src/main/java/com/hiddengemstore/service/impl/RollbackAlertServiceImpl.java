package com.hiddengemstore.service.impl;

import com.hiddengemstore.entity.RollbackFailureLog;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.IRollbackAlertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 回滚失败通知服务：用于发送短信/邮件告警（可插拔实现）
 * @author : ZhaoJH
 */
@Slf4j
@Service
public class RollbackAlertServiceImpl implements IRollbackAlertService {

    /**
     * 短信告警开关
     */
    @Value("${seckill.rollback.alert.sms.enabled:false}")
    private boolean smsEnabled;

    /**
     * 邮件告警开关
     */
    @Value("${seckill.rollback.alert.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * 短信接收人
     */
    @Value("${seckill.rollback.alert.sms.to:}")
    private String smsTo;

    /**
     * 邮件接收人
     */
    @Value("${seckill.rollback.alert.email.to:}")
    private String emailTo;

    /**
     * 告警去重窗口时间（秒）
     */
    @Value("${seckill.rollback.alert.dedup.window.seconds:300}")
    private long dedupWindowSeconds;

    @Resource
    private RedisCache redisCache;

    /**
     * 发送回滚失败告警通知，支持短信和邮件，具备去重能力
     * @param logEntity 回滚失败日志实体
     */
    @Override
    public void sendRollbackAlert(RollbackFailureLog logEntity) {
        try {
            if (!shouldNotify(logEntity.getVoucherId())) {
                return;
            }
            String content = formatContent(logEntity);
            if (smsEnabled && smsTo != null && !smsTo.isEmpty()) {
                log.warn("[ROLLBACK_SMS] to={} content={} ", smsTo, content);
            }
            if (emailEnabled && emailTo != null && !emailTo.isEmpty()) {
                log.warn("[ROLLBACK_EMAIL] to={} content={} ", emailTo, content);
            }
        } catch (Exception e) {
            log.warn("发送回滚失败通知异常", e);
        }
    }
    /**
     * 判断是否需要发送告警，通过Redis实现去重控制
     * @param voucherId 代金券ID
     * @return true-需要发送，false-在去重窗口内无需发送
     */
    private boolean shouldNotify(Long voucherId) {
        try {
            return redisCache.setIfAbsent(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ROLLBACK_ALERT_DEDUP_KEY,voucherId),
                    "1",
                    dedupWindowSeconds,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            return true;
        }
    }
    /**
     * 格式化告警内容
     * @param rollbackFailureLog 回滚失败日志
     * @return 格式化的告警消息
     */
    private String formatContent(RollbackFailureLog rollbackFailureLog) {
        String time =
                rollbackFailureLog.getCreateTime() == null ?
                        ""
                        :
                        rollbackFailureLog.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("回滚失败告警 | voucherId=%s userId=%s orderId=%s traceId=%s attempts=%s source=%s time=%s detail=%s",
                rollbackFailureLog.getVoucherId(),
                rollbackFailureLog.getUserId(),
                rollbackFailureLog.getOrderId(),
                rollbackFailureLog.getTraceId(),
                rollbackFailureLog.getRetryAttempts(),
                rollbackFailureLog.getSource(),
                time,
                rollbackFailureLog.getDetail());
    }
}