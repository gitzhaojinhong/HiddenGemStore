package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 优惠券状态
 * @author : ZhaoJH
 **/
public enum VoucherStatus {
    /**
     * 优惠券状态 
     * */
    AVAILABLE(1, "上架"),
    UNAVAILABLE(2, "下架"),
    EXPIRED(3, "过期");

    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, VoucherStatus> CODE_MAP = new HashMap<>();
    static {
        for (VoucherStatus e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    VoucherStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        VoucherStatus e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static VoucherStatus getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
