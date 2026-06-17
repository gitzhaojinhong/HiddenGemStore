package com.hiddengemstore.enums;

/**
 * 限流场景
 * @author : ZhaoJH
 */
public enum RateLimitScene {
    /** 申请访问令牌，用户操作资格凭证 */
    ISSUE_TOKEN,
    /** 下单（秒杀）接口 */
    SECKILL_ORDER
}
