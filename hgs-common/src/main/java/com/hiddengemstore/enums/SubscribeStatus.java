package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 是否删除秒杀优惠券订单记录
 * @author : ZhaoJH
 **/
public enum SubscribeStatus {
    /**
     * 是否删除秒杀优惠券订单记录
     * */
    UNSUBSCRIBED(0, "已取消订阅或未订阅"),
    
    SUBSCRIBED(1, "已订阅到券提醒（在队列中）"),
    
    SUCCESS(2,"自动发券已成功（已创建订单）")
    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, SubscribeStatus> CODE_MAP = new HashMap<>();
    static {
        for (SubscribeStatus e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    SubscribeStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        SubscribeStatus e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static SubscribeStatus getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
