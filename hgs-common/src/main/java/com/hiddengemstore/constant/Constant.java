package com.hiddengemstore.constant;

/**
 * 常量
 * @author : ZhaoJH
 **/
public class Constant {
    
    public static final String PREFIX_DISTINCTION_NAME = "prefix.distinction.name";
    
    public static final String DEFAULT_PREFIX_DISTINCTION_NAME = "hgs";
    
    public static final String SPRING_INJECT_PREFIX_DISTINCTION_NAME = "${"+PREFIX_DISTINCTION_NAME+":"+DEFAULT_PREFIX_DISTINCTION_NAME+"}";
    
    public static final String SECKILL_VOUCHER_TOPIC = "seckill_voucher_topic";
    
    public static final String SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC = "seckill_voucher_cache_invalidation_topic";
    
    public static final String BLOOM_FILTER_HANDLER_SHOP = "shop";
    // 秒杀卷布隆过滤器
    public static final String BLOOM_FILTER_HANDLER_VOUCHER = "voucher";
    
    public static final String DELAY_VOUCHER_REMINDER ="h_delay_voucher_reminder";
}
