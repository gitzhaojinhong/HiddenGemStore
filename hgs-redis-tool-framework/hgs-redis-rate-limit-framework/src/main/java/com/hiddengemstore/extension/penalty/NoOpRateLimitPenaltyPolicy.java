package com.hiddengemstore.extension.penalty;

import com.hiddengemstore.context.RateLimitContext;
import com.hiddengemstore.enums.BaseCode;

/**
 * 默认空实现
 * @author : ZhaoJH
 */
public class NoOpRateLimitPenaltyPolicy implements RateLimitPenaltyPolicy{
    @Override
    public void apply(RateLimitContext ctx, BaseCode reason) {
        // do nothing
    }
}
