package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单状态
 * @author : ZhaoJH
 **/
public enum OrderStatus {
    /**
     * 订单状态
     * */
    NORMAL(1, "正常"),
    
    CANCEL(2, "取消"),
    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, OrderStatus> CODE_MAP = new HashMap<>();
    static {
        for (OrderStatus e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    OrderStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        OrderStatus e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static OrderStatus getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
