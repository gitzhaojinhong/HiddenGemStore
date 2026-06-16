package com.hiddengemstore.kafka.rollbackredis;

import cn.hutool.core.collection.ListUtil;
import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.entity.RollbackFailureLog;
import com.hiddengemstore.enums.BaseCode;
import com.hiddengemstore.enums.LogType;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.enums.SeckillVoucherOrderOperate;
import com.hiddengemstore.lua.SeckillVoucherRollBackOperate;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.IRollbackAlertService;
import com.hiddengemstore.service.IRollbackFailureLogService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Redis 秒杀订单回滚数据操作组件
 * @author : ZhaoJH
 */
@Slf4j
@Component
public class RedisVoucherDataRollback {

    @Resource
    private SeckillVoucherRollBackOperate seckillVoucherRollBackOperate;

    @Resource
    private IRollbackFailureLogService rollbackFailureLogService;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private IRollbackAlertService rollbackAlertService;

    // 最大重试次数
    @Value("${seckill.rollback.retry.maxAttempts:3}")
    private int retryMaxAttempts;
    // 初始退避时间
    @Value("${seckill.rollback.retry.initialBackoffMillis:200}")
    private long initialBackoffMillis;
    // 最大退避时间
    @Value("${seckill.rollback.retry.maxBackoffMillis:1000}")
    private long maxBackoffMillis;

    /**
     * 回滚 Redis 中的秒杀数据，带指数退避重试。
     * @param seckillVoucherOrderOperate 是否执行订单恢复（YES/NO），传入 Lua 作为操作码
     * @param traceId                    贯穿回滚流程的链路 ID（便于串查）
     * @param voucherId                  优惠券 ID
     * @param userId                     用户 ID
     * @param orderId                    订单 ID
     */
    public void rollbackRedisVoucherData(SeckillVoucherOrderOperate seckillVoucherOrderOperate,
                                         Long traceId,
                                         Long voucherId,
                                         Long userId,
                                         Long orderId,
                                         Integer beforeQty,
                                         Integer changeQty,
                                         Integer afterQty) {
        // 构建 Lua 执行参数：KEY 列表包含库存、已购用户集合、trace 日志集合
        List<String> keys = ListUtil.of(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId).getRelKey()
        );
        // Lua ARGV：voucherId、userId、orderId、操作码、traceId、日志类型（恢复）
        String[] args = new String[9];
        args[0] = String.valueOf(voucherId);
        args[1] = String.valueOf(userId);
        args[2] = String.valueOf(orderId);
        args[3] = String.valueOf(seckillVoucherOrderOperate.getCode());
        args[4] = String.valueOf(traceId);
        args[5] = String.valueOf(LogType.RESTORE.getCode());
        args[6] = String.valueOf(beforeQty);
        args[7] = String.valueOf(changeQty);
        args[8] = String.valueOf(afterQty);
        // 带退避的重试，返回最终结果码（成功返回 SUCCESS 码）
        Integer finalCode = luaRollbackWithResultCode(keys, args, retryMaxAttempts, initialBackoffMillis, maxBackoffMillis);
        boolean ok = finalCode != null && finalCode.equals(BaseCode.SUCCESS.getCode());
        if (!ok) {
            String reason = BaseCode.getMsg(finalCode == null ? -1 : finalCode);
            // 结构化错误日志：包含 voucher/user/order/trace 与失败原因
            log.error("Redis回滚最终失败|voucherId={}|userId={}|orderId={}|traceId={} reason={}", voucherId, userId, orderId, traceId, reason);
            // 失败日志：异常-恢复失败，记录 Lua 返回码供后续精准路由与统计
            saveRollbackFailureLog(voucherId, userId, orderId, traceId, "redis rollback failed after retries: " + reason, finalCode);
            // 指标：最终放弃（用尽重试次数）
            safeInc("seckill_rollback_retry_give_up", "component", "redis_voucher_data");
        }
    }
    /**
     * 执行 Lua 回滚并进行指数退避重试，返回最终的 Lua 结果码。
     * 成功：返回 BaseCode.SUCCESS 对应的码，并上报重试成功指标。
     * 失败：用尽重试后返回最后一次失败的结果码（异常时为 -1）。
     */
    private Integer luaRollbackWithResultCode(List<String> keys, String[] args, int retryMaxAttempts, long initialBackoffMillis, long maxBackoffMillis) {
        // 当前重试次数（从 0 开始计数，日志展示为 attempt+1）
        int attempt = 0;
        // 初始退避时间，设置下限避免过小
        long backoff = Math.max(50, initialBackoffMillis);
        // 结果码
        Integer lastCode;
        while (true) {
            try {
                Integer result = seckillVoucherRollBackOperate.execute(keys, args);
                lastCode = result;
                if (result != null && result.equals(BaseCode.SUCCESS.getCode())) {
                    // 指标：重试最终成功（包含首尝即成功和多次后成功）
                    safeInc("seckill_rollback_retry_success", "component", "redis_voucher_data");
                    return result;
                }
                // 非成功码，打印原因并继续重试
                String reason = BaseCode.getMsg(result == null ? -1 : result);
                log.warn("Redis回滚失败，准备重试|attempt={} reason={}", attempt + 1, reason);
            } catch (Exception e) {
                // 异常场景以 -1 兜底作为失败码
                lastCode = -1;
                log.warn("Redis回滚异常，准备重试|attempt={} error={}", attempt + 1, e.getMessage());
            }
            attempt++;
            if (attempt >= retryMaxAttempts) {
                // 用尽重试次数，结束循环
                break;
            }
            // 退避 + 抖动（jitter）：避免热点一致重试导致雪崩
            sleepQuietly(withJitter(backoff));
            backoff = Math.min(backoff * 2, Math.max(backoff, maxBackoffMillis));
        }
        // 返回最后一次的结果码，供失败日志与后续路由使用
        return lastCode;
    }
    /**
     * 退避抖动：在基础退避时间上增加约 15% 的随机抖动，降低同步重试风险。
     */
    private long withJitter(long base) {
        long jitter = Math.round(base * 0.15 * Math.random());
        return base + jitter;
    }
    /**
     * 静默休眠工具方法
     * 原生Thread.sleep/TimeUnit.sleep在线程被中断时，会抛出中断异常打断整个流程。
     * 本方法捕获休眠中断异常，不把异常抛出到上层，保证主业务流程正常走完；
     * 同时调用interrupt()恢复线程中断标记，不会丢失“线程被中断”这个信号。
     * 后续代码如果需要感知中断，可通过 Thread.currentThread().isInterrupted() 判断。
     */
    private void sleepQuietly(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    /**
     * 保存回滚最终失败的结构化日志，并进行指标上报与外部告警。
     * 字段包含：voucherId/userId/orderId/traceId/detail/retryAttempts/resultCode/source；
     * 同时：上报失败计数(seckill_rollback_failure)与原因标签(retry_exhausted)，调用告警服务。
     */
    private void saveRollbackFailureLog(Long voucherId, Long userId, Long orderId, Long traceId, String detail, Integer resultCode) {
        try {
            RollbackFailureLog logEntity = new RollbackFailureLog();
            logEntity.setId(snowflakeIdGenerator.nextId())
                    .setOrderId(orderId)
                    .setUserId(userId)
                    .setVoucherId(voucherId)
                    .setDetail(detail)
                    .setResultCode(resultCode)
                    .setTraceId(traceId)
                    .setRetryAttempts(retryMaxAttempts)
                    .setSource("redis_voucher_data")
                    .setCreateTime(LocalDateTime.now())
                    .setUpdateTime(LocalDateTime.now());
            // 入库失败日志
            rollbackFailureLogService.save(logEntity);
            // 计数指标：失败总量 + 原因（重试用尽）。避免高基数标签！
            safeInc("seckill_rollback_failure", "component", "redis_voucher_data");
            safeInc("seckill_rollback_failure", "reason", "retry_exhausted");
            // 触发外部告警（内部已做按 voucherId 窗口去重）
            rollbackAlertService.sendRollbackAlert(logEntity);
        } catch (Exception e) {
            log.warn("保存回滚失败日志异常", e);
        }
    }
    /**
     * 安全递增监控指标计数器，异常时静默忽略避免影响主流程
     * @param name 指标名称
     * @param tagKey 标签键
     * @param tagValue 标签值
     */
    private void safeInc(String name, String tagKey, String tagValue) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter(name, tagKey, tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }
}
