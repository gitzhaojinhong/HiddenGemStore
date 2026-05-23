package com.hiddengemstore.core;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import com.hiddengemstore.model.WorkDataCenterId;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static com.hiddengemstore.core.IdGeneratorConstant.*;


/**
 * 雪花算法ID生成器
 * 基于 MyBatis-Plus 源码魔改优化
 * 支持：手动指定ID / Redis自动分配ID / 本地MAC+PID自动生成ID
 * @author : Zhao
 */
@Slf4j
public class SnowflakeIdGenerator {

    /**
     * 基准时间：2010-11-04 09:42:54
     * 一旦确定不能修改，保证ID长度与趋势递增
     */
    private static final long BASIS_TIME = 1288834974657L;

    // ============================== 核心位配置 ==============================
    /** 序列号所占位数 12bit → 每毫秒支持 4096 个ID */
    private final long sequenceBits = 12L;

    // ============================== 实例成员变量 ==============================
    /** 机器ID */
    private final long workerId;
    /** 数据中心ID */
    private final long datacenterId;
    /** 毫秒内序列号 0~4095 */
    private long sequence = 0L;
    /** 上一次生成ID的时间戳 */
    private long lastTimestamp = -1L;
    /** 本机IP地址（用于获取MAC地址） */
    private InetAddress inetAddress;

    // ============================== 构造方法 ==============================

    /**
     * 【推荐】通过 Redis 分配的 WorkDataCenterId 构造
     * 分布式环境优先使用，保证唯一不重复
     */
    public SnowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
        if (Objects.nonNull(workDataCenterId.getDataCenterId())) {
            // 使用Redis分配的机器ID+数据中心ID
            this.workerId = workDataCenterId.getWorkId();
            this.datacenterId = workDataCenterId.getDataCenterId();
        } else {
            // 兜底：本地自动生成
            this.datacenterId = getDatacenterId();
            this.workerId = getMaxWorkerId(datacenterId);
        }
    }

    /**
     * 通过指定IP地址构造
     */
    public SnowflakeIdGenerator(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        this.datacenterId = getDatacenterId();
        this.workerId = getMaxWorkerId(datacenterId);
        initLog();
    }

    /**
     * 手动指定机器ID + 数据中心ID
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        Assert.isFalse(workerId > MAX_WORKER_ID || workerId < 0,
                String.format("机器ID不能大于 %d 或小于 0", MAX_WORKER_ID));
        Assert.isFalse(datacenterId > MAX_DATA_CENTER_ID || datacenterId < 0,
                String.format("数据中心ID不能大于 %d 或小于 0", MAX_DATA_CENTER_ID));
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        initLog();
    }

    /**
     * 初始化日志：打印机器ID与数据中心ID
     */
    private void initLog() {
        if (log.isDebugEnabled()) {
            log.debug("雪花算法生成器初始化完成 → 数据中心ID:{} 机器ID:{}", this.datacenterId, this.workerId);
        }
    }

    // ============================== 自动生成机器ID/数据中心ID ==============================

    /**
     * 根据 数据中心ID + 进程PID 生成机器ID
     * 保证单机多进程不重复
     */
    protected long getMaxWorkerId(long datacenterId) {
        StringBuilder mPid = new StringBuilder();
        mPid.append(datacenterId);
        // 获取进程PID
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (StringUtils.isNotBlank(name)) {
            mPid.append(name.split("@")[0]);
        }
        // 取hash后取模，保证落在 0~maxWorkerId 范围内
        return (mPid.toString().hashCode() & 0xffff) % (MAX_WORKER_ID + 1);
    }

    /**
     * 根据本机MAC地址生成数据中心ID
     * 保证多机器不重复
     */
    protected long getDatacenterId() {
        long id = 0L;
        try {
            if (null == this.inetAddress) {
                this.inetAddress = InetAddress.getLocalHost();
            }
            NetworkInterface network = NetworkInterface.getByInetAddress(this.inetAddress);
            if (null == network) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                if (null != mac) {
                    // 通过MAC地址计算唯一标识
                    id = ((0x000000FF & (long) mac[mac.length - 2]) | (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
                    id = id % (MAX_DATA_CENTER_ID + 1);
                }
            }
        } catch (Exception e) {
            log.warn("获取数据中心ID异常: {}", e.getMessage());
        }
        return id;
    }

    // ============================== 核心ID生成逻辑 ==============================

    /**
     * 抽取公共逻辑：时钟回拨处理 + 序列号生成
     */
    private long getBase() {
        int maxOffset = 5;
        long timestamp = timeGen();

        // ============ 处理时钟回拨（服务器时间同步异常） ============
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= maxOffset) {
                try {
                    // 等待2倍偏移时间后重试
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException(String.format("时钟回拨，拒绝生成ID %d 毫秒", offset));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(String.format("时钟回拨严重，拒绝生成ID %d 毫秒", offset));
            }
        }

        // ============ 序列号自增逻辑 ============
        // 同一毫秒内 → 序列号+1
        if (lastTimestamp == timestamp) {
            final long sequenceMask = ~(-1L << sequenceBits);
            sequence = (sequence + 1) & sequenceMask;
            // 序列号耗尽 → 等待下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒 → 重置为 1~2 随机数（魔改点：避免从0开始）
            sequence = ThreadLocalRandom.current().nextLong(1, 3);
        }

        lastTimestamp = timestamp;
        return timestamp;
    }

    /**
     * 对外提供：生成下一个分布式ID
     * 加锁保证线程安全
     */
    public synchronized long nextId() {
        long timestamp = getBase();

        // 位移计算
        final long datacenterIdShift = sequenceBits + WORKER_ID_BITS;
        /* 数据中心ID所占位数 5bit → 支持 0-31 */
        final long timestampLeftShift = sequenceBits + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

        // 拼接最终ID
        return ((timestamp - BASIS_TIME) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << sequenceBits)
                | sequence;
    }

    /**
     * 循环等待，直到获取下一毫秒时间戳
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取系统时间（使用 SystemClock 高性能缓存时间）
     */
    protected long timeGen() {
        return SystemClock.now();
    }
}