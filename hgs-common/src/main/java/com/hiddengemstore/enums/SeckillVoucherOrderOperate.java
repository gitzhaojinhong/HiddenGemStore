package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 是否删除秒杀优惠券订单记录
 * @author : ZhaoJH
 **/
public enum SeckillVoucherOrderOperate {
    /**
     * 是否删除秒杀优惠券订单记录
     * */
    NO(0, "不删除"),
    YES(1, "删除"),
    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, SeckillVoucherOrderOperate> CODE_MAP = new HashMap<>();
    static {
        for (SeckillVoucherOrderOperate e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    SeckillVoucherOrderOperate(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        SeckillVoucherOrderOperate e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static SeckillVoucherOrderOperate getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
