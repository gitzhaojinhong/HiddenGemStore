package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 接口返回code码
 * @author : ZhaoJH
 **/
public enum BaseCode {
    /**
     * 基础code码
     * */
    SUCCESS(0, "OK"),
    
    SECKILL_VOUCHER_NOT_EXIST(10001, "秒杀优惠券不存在"),
    
    SECKILL_VOUCHER_NOT_BEGIN(10002, "秒杀优惠券未开始"),
    
    SECKILL_VOUCHER_IS_OVER(10003, "秒杀优惠券已结束"),
    
    SECKILL_VOUCHER_STOCK_NOT_EXIST(10004, "秒杀优惠券库存不存在"),
    
    SECKILL_VOUCHER_STOCK_INSUFFICIENT(10005, "秒杀优惠券库存不足"),
    
    SECKILL_VOUCHER_CLAIM(10006, "秒杀优惠券已领取"),
    
    SECKILL_RATE_LIMIT_IP_EXCEEDED(10007, "请求过于频繁，请稍后再试"),
    
    SECKILL_RATE_LIMIT_USER_EXCEEDED(10008, "操作过于频繁，请稍后再试"),
    
    SECKILL_VOUCHER_ORDER_NOT_EXIST(10009, "优惠券订单不存在"),
    
    AFTER_SECKILL_VOUCHER_REMAIN_STOCK_NOT_NEGATIVE_NUMBER(10010,"修改后的剩余库存不能为负数"),
    
    VOUCHER_UNAVAILABLE(10011,"优惠券已下架"),
    
    VOUCHER_EXPIRED(10012,"优惠券已过期"),
    
    VOUCHER_ORDER_EXIST(10013,"优惠券订单已存在"),
    
    VOUCHER_ORDER_CANCEL(10014,"优惠券订单已取消"),
    
    USER_NOT_EXIST(20000, "用户不存在"),
    
    USER_ALREADY_PURCHASE(20001, "用户已经购买"),
    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, BaseCode> CODE_MAP = new HashMap<>();
    static {
        for (BaseCode e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    BaseCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        BaseCode e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static BaseCode getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
