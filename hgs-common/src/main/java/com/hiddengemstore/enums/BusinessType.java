package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务类型
 * @author : ZhaoJH
 **/
public enum BusinessType {
    /**
     * 业务类型
     * */
    SUCCESS(1, "创建订单成功"),
    TIMEOUT(2, "创建订单超时"),
    FAIL(3, "创建订单失败"),
    CANCEL(4, "主动取消"),


    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, BusinessType> CODE_MAP = new HashMap<>();
    static {
        for (BusinessType e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    BusinessType(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        BusinessType e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static BusinessType getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
