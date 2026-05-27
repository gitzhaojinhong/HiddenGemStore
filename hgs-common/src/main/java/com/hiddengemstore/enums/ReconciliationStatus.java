package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 对账状态
 * @author : ZhaoJH
 **/
public enum ReconciliationStatus {
    /**
     * 对账状态
     * */
    PENDING(1, "待处理"),
    ABNORMAL(2, "异常"),
    INCONSISTENT(3, "不一致"),
    CONSISTENT(4, "一致"),

    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, ReconciliationStatus> CODE_MAP = new HashMap<>();
    static {
        for (ReconciliationStatus e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    ReconciliationStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        ReconciliationStatus e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static ReconciliationStatus getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
