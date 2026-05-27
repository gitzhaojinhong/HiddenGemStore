package com.hiddengemstore.constants;

/**
 * 锁信息类型常量
 * @author : ZhaoJH
 */
public interface LockInfoType {
    /**
     * 防重复执行幂等类型
     */
    String REPEAT_EXECUTE_LIMIT = "repeat_execute_limit";
    /**
     * 分布式服务锁类型
     */
    String SERVICE_LOCK = "service_lock";
}
