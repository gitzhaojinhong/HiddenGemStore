package com.hiddengemstore.extension.penalty;

import com.hiddengemstore.context.RateLimitContext;
import com.hiddengemstore.enums.BaseCode;

/**
 * 惩罚策略扩展点：在命中限流后可执行封禁、打标、告警等动作。
 * @author : ZhaoJH
 */
public interface RateLimitPenaltyPolicy {
    /**
     * 应用惩罚策略
     * @param context    当前限流上下文
     * @param reason 命中原因（IP/USER 限流等）
     */
    void apply(RateLimitContext context, BaseCode reason);
}
