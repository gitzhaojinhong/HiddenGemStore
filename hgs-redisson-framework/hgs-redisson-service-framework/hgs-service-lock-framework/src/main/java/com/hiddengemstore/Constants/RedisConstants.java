package com.hiddengemstore.Constants;

/**
 * redis 常量
 * @author : ZhaoJH
 */
public interface RedisConstants {
    String LOGIN_CODE_KEY = "login:code:";
    Long LOGIN_CODE_TTL = 2L;
    String LOGIN_USER_KEY = "login:token:";
    Long LOGIN_USER_TTL = 36000L;
    // 空值过期时间
    Long CACHE_NULL_TTL = 2L;

    Long CACHE_SHOP_TTL = 30L;
    String CACHE_SHOP_KEY = "cache:shop:";

    String LOCK_SHOP_KEY = "lock:shop:";
    Long LOCK_SHOP_TTL = 10L;

    // 秒杀券锁
    String LOCK_SECKILL_VOUCHER_KEY = "lock_seckill_voucher";

    String LOCK_SECKILL_VOUCHER_STOCK_KEY = "lock_seckill_voucher_stock";

    String SECKILL_STOCK_KEY = "seckill:stock:";
    String BLOG_LIKED_KEY = "blog:liked:";
    String FEED_KEY = "feed:";
    String SHOP_GEO_KEY = "shop:geo:";
    String USER_SIGN_KEY = "sign:";
}
