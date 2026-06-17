package com.hiddengemstore.execute;

import com.hiddengemstore.enums.RateLimitScene;

/**
 * 限流执行接口
 * @author : ZhaoJH
 */
public interface RateLimitHandler {

    void execute(Long voucherId, Long userId, RateLimitScene scene);
}
