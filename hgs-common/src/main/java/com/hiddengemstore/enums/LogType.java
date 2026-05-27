package com.hiddengemstore.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 记录类型
 * @author : ZhaoJH
 **/
public enum LogType {
    /**
     * 记录类型
     * */
    DEDUCT(-1, "扣减"),
    
    RESTORE(1, "恢复"),
    ;

    /** code → 枚举实例的缓存映射，类加载时初始化，避免每次查找都遍历 values() */
    private static final Map<Integer, LogType> CODE_MAP = new HashMap<>();
    static {
        for (LogType e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    @Getter
    private final Integer code;
    
    private String msg = "";
    
    LogType(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        LogType e = CODE_MAP.get(code);
        return e != null ? e.msg : "";
    }

    public static LogType getRc(Integer code) {
        return CODE_MAP.get(code);
    }
}
