package com.hiddengemstore.constant;

/**
 * 分布式锁 业务名管理
 * @author : ZhaoJH
 **/
public class DistributedLockConstants {
    
    public final static String UPDATE_USER_INFO_LOCK = "hgs_update_user_info_lock";

    /**
     * 秒杀券更新锁
     */
    public final static String UPDATE_SECKILL_VOUCHER_LOCK = "hgs_update_seckill_voucher_lock";
    
    public final static String UPDATE_SECKILL_VOUCHER_STOCK_LOCK = "hgs_update_seckill_voucher_stock_lock";
}
