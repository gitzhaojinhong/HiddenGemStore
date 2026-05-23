package com.hiddengemstore.core;

/**
 * 雪花算法相关常量定义（接口属性天然就是public static final）
 * @author : Zhao
 */
public interface IdGeneratorConstant {
    /**
     * 雪花算法位分配 :
     * 符号位(1) | 时间戳(41) | datacenterId(5) | workerId(5) | 序列号(12)
     *    0     |  timestamp |   0-31          |   0-31      |  0-4095
     * ---------------------------------------------------------------------------
     * ~(-1L << worker_id_bits)说明:
     * 位运算 = CPU 原生指令，1 个时钟周期完成
     * ~(-1L << worker_id_bits) = 2的n次方-1
     */
    // 机器id 5位
    long WORKER_ID_BITS = 5L;
    // 数据中心id 5位
    long DATA_CENTER_ID_BITS = 5L;
    // 最大机器id 31
    long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    // 最大数据中心id 31
    long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
}
