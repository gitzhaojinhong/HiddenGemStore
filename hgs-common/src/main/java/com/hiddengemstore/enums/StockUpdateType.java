package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 库存操作类型
 * @author : ZhaoJH
 **/
public enum StockUpdateType {
    /**
     * 库存操作类型
     * */
    DECREASE(-1, "扣减"),
    
    INCREASE(1, "增加"),
    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, StockUpdateType> CODE_MAP = new HashMap<>();
    static {
        for (StockUpdateType e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    StockUpdateType(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        StockUpdateType e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static StockUpdateType getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
