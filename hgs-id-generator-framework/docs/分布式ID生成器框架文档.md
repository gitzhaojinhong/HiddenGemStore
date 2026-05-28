# 分布式ID生成器框架文档

## 一、背景

### 1.1 分布式ID的作用

分布式ID在构建大规模分布式系统时扮演着至关重要的角色，主要用于确保在分布式环境中数据的唯一性和一致性。以下是分布式ID的几个主要作用：

1. **确保唯一性**：在分布式系统中，可能有成千上万个实例同时请求ID。分布式ID生成系统能保证即使在高并发的情况下也能生成全局唯一的ID，避免数据冲突和覆盖。

2. **便于水平扩展**：分布式系统通常需要水平扩展以支持更多的用户和业务。分布式ID生成机制允许系统在不同的机器、数据中心甚至地理区域中扩展，同时仍然能够生成唯一的ID，无需担心ID冲突。

3. **提高性能**：通过避免依赖中心化的数据库序列生成ID，分布式ID生成机制可以显著提高应用性能。这些机制通常在内存中进行，减少了网络延迟和磁盘I/O，从而加快了ID的生成速度。

4. **减少系统依赖**：分布式ID生成不依赖特定的数据库或存储系统，减少了系统组件之间的耦合。这种独立性使得系统更加健壮，减少了因数据库故障导致的ID生成问题。

5. **时间有序性**：某些分布式ID生成策略（如雪花算法）能够生成大致按时间顺序递增的ID。这对于需要跟踪记录创建顺序或进行时间序列分析的应用来说是一个重要特性。

6. **支持事务和日志追踪**：在复杂的分布式系统中，分布式ID可以用来追踪和管理跨多个系统和组件的事务和日志。每个操作都可以关联一个唯一ID，使得问题定位和性能监控变得更加容易。

7. **安全性和隐私保护**：通过生成不可预测的唯一ID，分布式ID机制还可以增加系统的安全性，防止恶意用户通过ID预测和访问未授权的数据。

### 1.2 雪花算法简介

对于分布式ID应用最出名的莫过于经典的雪花算法（Snowflake）。雪花算法是 Twitter 开源的分布式 ID 生成算法，核心思想是使用一个 64 bit 的 Long 型数字作为全局唯一ID。

**结构**

雪花算法生成 ID 的结果是一个 64bit 大小的整数，结构如下：

```
0 | 00000000 00000000 00000000 00000000 00000000 0 | 00000 00000 | 000000000000
```

| 部分 | 位数 | 说明 |
|------|------|------|
| 符号位 | 1 bit | 固定为0，表示正数 |
| 时间戳 | 41 bit | 毫秒级时间戳，可使用约69年 |
| 机器标识 | 10 bit | 可拆分为 5位 datacenterId + 5位 workerId，最多支持 1024 个节点 |
| 序列号 | 12 bit | 同一毫秒内的自增序列，每毫秒支持 4096 个ID |

**特点**

- 在 Java 中 64bit 的整数是 Long 类型，所以雪花算法生成的 ID 用 long 存储。
- 对于每一个雪花算法服务，需要先指定 10 位的机器码，可根据自身业务设定（如机房号+机器号、机器号+服务号等）。

**优点**

- 高并发分布式环境下生成不重复 ID，每秒可生成百万个不重复 ID。
- 基于时间戳，以及同一时间戳下序列号自增，基本保证 ID 有序递增。
- 不依赖第三方库或者中间件。
- 算法简单，在内存中进行，效率高。

**缺点**

- 依赖服务器时间，服务器时钟回拨时可能会生成重复 ID。算法中可通过记录最后一个生成 ID 时的时间戳来解决，每次生成 ID 之前比较当前服务器时钟是否被回拨，避免生成重复 ID。

**注意事项**

- 雪花算法每一部分占用的比特位数量并不是固定的。例如业务可能达不到 69 年之久，可减少时间戳占用的位数；如果服务需要部署的节点超过 1024 台，可将减少的位数补充给机器码。
- 41 位时间戳不是直接存储当前服务器毫秒时间戳，而是当前服务器时间戳减去某一个初始时间戳值（基准时间），一般使用服务上线时间作为初始时间戳值。
- 对于机器码，可根据自身情况做调整（机房号、服务器号、业务号、机器 IP 等），只要部署的不同服务中最终计算出来的机器码能区分开来即可。

### 1.3 MyBatis-Plus 雪花算法在 K8s 环境下的重复问题

**问题现象**

数据库的业务 ID 添加了唯一索引，当并发量上来时生产环境偶尔会出现此列的值重复问题。这是因为生成 ID 时发生了重复现象，采取的是 MyBatis-Plus 的雪花算法策略。雪花算法大致由 4 部分组成：时间戳、datacenterId、workerId、自增序列。

在 MyBatis-Plus 中，datacenterId 和 workerId 需要开发者自己设置，如果没有设置则 MyBatis-Plus 会自动进行设值。

**根因分析**

MyBatis-Plus 的雪花算法实现依赖于机器的 MAC 地址和进程 ID 来生成 `datacenterId` 和 `workerId`。在传统的物理机或虚拟机环境中，这通常能保证唯一性。然而在 Kubernetes 这样的容器化环境中：

- 容器内的网络接口 MAC 地址可能由虚拟化技术生成，存在重复的可能性。
- 容器内 Java 应用的进程 ID（PID）经常被固定为 1（例如在 Alpine 等基础镜像中）。

当多个服务实例的 MAC 地址和 PID 都相同时，它们生成的 `datacenterId` 和 `workerId` 就会相同，在并发和高频 ID 生成的场景下，就有概率产生重复的全局 ID。

**源码分析**

服务启动时，会加载默认的 `DefaultIdentifierGenerator`，调用无参构造方法：

```java
public class DefaultIdentifierGenerator implements IdentifierGenerator {
    private final Sequence sequence;

    public DefaultIdentifierGenerator() {
        this.sequence = new Sequence(null);
    }

    public DefaultIdentifierGenerator(InetAddress inetAddress) {
        this.sequence = new Sequence(inetAddress);
    }

    public DefaultIdentifierGenerator(long workerId, long dataCenterId) {
        this.sequence = new Sequence(workerId, dataCenterId);
    }

    public DefaultIdentifierGenerator(Sequence sequence) {
        this.sequence = sequence;
    }

    @Override
    public Long nextId(Object entity) {
        return sequence.nextId();
    }
}
```

接着调用无参构造方法时构造了 `Sequence`，传入的 `InetAddress` 参数为 null：

```java
private final long workerIdBits = 5L;
private final long datacenterIdBits = 5L;
private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

public Sequence(InetAddress inetAddress) {
    this.inetAddress = inetAddress;
    this.datacenterId = getDatacenterId(maxDatacenterId);
    this.workerId = getMaxWorkerId(datacenterId, maxWorkerId);
}
```

`maxDatacenterId` 和 `maxWorkerId` 固定为 31。

**getDatacenterId** 方法通过 MAC 地址计算 datacenterId：

```java
protected long getDatacenterId(long maxDatacenterId) {
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
                id = ((0x000000FF & (long) mac[mac.length - 2]) | (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
                id = id % (maxDatacenterId + 1);
            }
        }
    } catch (Exception e) {
        logger.warn(" getDatacenterId: " + e.getMessage());
    }
    return id;
}
```

**getMaxWorkerId** 方法通过 MAC 地址 + 进程 PID 的 hashCode 计算 workerId：

```java
protected long getMaxWorkerId(long datacenterId, long maxWorkerId) {
    StringBuilder mpid = new StringBuilder();
    mpid.append(datacenterId);
    String name = ManagementFactory.getRuntimeMXBean().getName();
    if (StringUtils.isNotBlank(name)) {
        mpid.append(name.split(StringPool.AT)[0]);
    }
    // MAC + PID 的 hashcode 获取16个低位
    return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
}
```

**结论**：在 K8s 集群环境下，如果不是在同一个 K8s 环境中，MAC 地址有可能会重复，Java 服务进程 ID 都为 1，这就造成生成的 ID 可能重复。

**解决方案**

避免依赖不可靠的本地环境信息，改为使用外部集中式服务来分配全局唯一的 `workerId` 和 `datacenterId`。可以使用 Redis 或 Zookeeper 来实现。例如在应用启动时，向 Redis 申请一个唯一的 ID 组合，确保整个集群内每个实例的标识都是独一无二的，从而从根本上杜绝因机器标识冲突导致的 ID 重复问题。

---

## 二、分布式ID组件的使用

### 2.1 依赖

```xml
<dependency>
    <groupId>com.hgs</groupId>
    <artifactId>hgs-id-generator-framework</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2.2 Redis 配置

```yaml
spring:
  data:
    redis:
      database: 0
      host: 127.0.0.1
      port: 6379
```

### 2.3 使用方式

```java
@Resource
private SnowflakeIdGenerator snowflakeIdGenerator;

public void testId() {
    long id = snowflakeIdGenerator.nextId();
}
```

---

## 三、分布式ID组件的实现原理

本组件没有选择适配 MyBatis-Plus 的 `IdentifierGenerator` 接口，原因是为了解耦框架依赖——如果以后出现比 MyBatis-Plus 更高效的持久化框架，可以更方便地替换。因此选择直接将 MyBatis-Plus 的雪花算法移植到组件中，并进行了优化改造。

核心改造点：**通过 Redis 的 Lua 脚本来分配全局唯一的 `workerId` 和 `dataCenterId`**，替代 MyBatis-Plus 默认的基于本地 MAC 地址和 PID 的生成策略。

### 3.1 整体架构

组件由以下核心类组成：

| 类名 | 职责 |
|------|------|
| `WorkDataCenterId` | workerId 和 dataCenterId 的数据模型 |
| `IdGeneratorConstant` | 雪花算法相关常量定义 |
| `WorkAndDataCenterIdHandler` | 通过 Redis Lua 脚本分配 workerId 和 dataCenterId |
| `SnowflakeIdGenerator` | 雪花算法 ID 生成器（核心） |
| `IdGeneratorAutoConfig` | Spring Boot 自动装配配置类 |

装配流程：`IdGeneratorAutoConfig` → 创建 `WorkAndDataCenterIdHandler` → 执行 Lua 脚本获取 `WorkDataCenterId` → 注入 `SnowflakeIdGenerator`。

### 3.2 IdGeneratorAutoConfig（自动装配）

```java
@Configuration
public class IdGeneratorAutoConfig {
    /**
     * 自动装配工作节点和数据中心ID分配处理器，作为workDataCenterId方法的参数
     */
    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate){
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }
    /**
     * 从Redis获取并分配工作节点ID和数据中心ID，作为雪snowflakeIdGenerator方法的参数
     */
    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler){
        return workAndDataCenterIdHandler.getWorkAndDataCenterId();
    }
    /**
     * 自动装配雪花算法ID生成器
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId){
        return new SnowflakeIdGenerator(workDataCenterId);
    }
}
```

通过 Spring Boot 的自动装配机制（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`），无需手动配置即可自动加载。

### 3.3 IdGeneratorConstant（常量定义）

雪花算法的位分配常量，使用接口定义（接口属性天然就是 `public static final`）：

```java
public interface IdGeneratorConstant {
    // 符号位(1) | 时间戳(41) | datacenterId(5) | workerId(5) | 序列号(12)
    //    0     |  timestamp |   0-31          |   0-31      |  0-4095

    // 机器id 5位
    long WORKER_ID_BITS = 5L;
    // 数据中心id 5位
    long DATA_CENTER_ID_BITS = 5L;
    // 最大机器id 31
    long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    // 最大数据中心id 31
    long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
}
```

其中 `~(-1L << n)` 是位运算技巧，等价于 `2^n - 1`，结果为 31。

### 3.4 WorkAndDataCenterIdHandler（机器ID和数据中心ID的分配）

负责通过 Redis Lua 脚本原子性地分配 `workerId` 和 `dataCenterId`：

```java
/**
 * 工作节点ID和数据中心ID的分布式分配处理器,负责通过Redis Lua脚本.
 * 原子性地分配workerId（工作节点ID）和dataCenterId（数据中心ID）。
 */
@Slf4j
public class WorkAndDataCenterIdHandler {
    private final String SNOWFLAKE_WORK_ID_KEY = "snowflake_work_id";
    private final String SNOWFLAKE_DATA_CENTER_ID_key = "snowflake_data_center_id";

    private final List<String> keys = Arrays.asList(SNOWFLAKE_WORK_ID_KEY, SNOWFLAKE_DATA_CENTER_ID_key);

    private final StringRedisTemplate stringRedisTemplate;
    private DefaultRedisScript<String> redisScript;

    /**
     * 构造函数初始化 stringRedisTemplate 和 redisScript
     */
    public WorkAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/workAndDataCenterId.lua")));
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("初始化Redis Lua脚本失败", e);
        }
    }
    /**
     * 获取工作节点ID和数据中心ID
     */
    public WorkDataCenterId getWorkAndDataCenterId() {
        WorkDataCenterId workDataCenterId = new WorkDataCenterId();
        try {
            // 参数传入: 最大工作节点ID, 最大数据中心ID
            Object[] data = new String[2];
            data[0] = String.valueOf(IdGeneratorConstant.MAX_WORKER_ID);
            data[1] = String.valueOf(IdGeneratorConstant.MAX_DATA_CENTER_ID);
            // 执行Lua脚本 参数传入: keys, data
            String result = stringRedisTemplate.execute(redisScript, keys, data);
            // 解析结果
            workDataCenterId = JSON.parseObject(result, WorkDataCenterId.class);
        } catch (Exception e) {
            log.error("获取工作节点ID和数据中心ID失败", e);
        }
        return workDataCenterId;
    }
}
```

### 3.5 WorkDataCenterId（数据模型）

```java
/**
 * 工作节点ID和数据中心ID的数据模型
 * @author : Zhao
 */
@Data
public class WorkDataCenterId {
    private Long workId;
    private Long dataCenterId;
}
```

`WorkAndDataCenterIdHandler` 执行 Lua 脚本后获得 `WorkDataCenterId` 实体，包含了 `workId` 和 `dataCenterId`。在 `WorkDataCenterId` 注入到 Spring 上下文的过程中，就调用了 `WorkAndDataCenterIdHandler#getWorkAndDataCenterId` 方法从 Redis 加载 `workId` 和 `dataCenterId`。

### 3.6 workAndDataCenterId.lua（Lua 分配脚本）

Lua 脚本负责在 Redis 中原子性地分配全局唯一的 `workId` 和 `dataCenterId`：

```lua
--- 用于为分布式ID生成器分配唯一的workId和dataCenterId

--- 获取workId和dataCenterId的Redis key
local snowflake_work_id_key = KEYS[1]
local snowflake_data_center_id_key = KEYS[2]

--- 获取workId和dataCenterId的最大值，用于判断是否达到上线
local max_work_id = tonumber(ARGV[1])
local max_data_center_id = tonumber(ARGV[2])

--- 用于返回的workId和dataCenterId
local return_work_id = 0
local return_data_center_id = 0

--- workId和dataCenterId的初始化标志，默认为false，表示redis中不存在
local snowflake_work_id_flag = false
local snowflake_data_center_id_flag = false

--- 判断redis中是否存在workId和dataCenterId,如果没有就执行set初始化,并标记初始化标准为true
if (redis.call('exists', snowflake_work_id_key) == 0) then
    redis.call('set', snowflake_work_id_key, 0)
    snowflake_work_id_flag = true
end
if (redis.call('exists', snowflake_data_center_id_key) == 0) then
    redis.call('set', snowflake_data_center_id_key, 0)
    snowflake_data_center_id_flag = true
end

--- 如果初始化标准都为true,表示workId和dataCenterId都是刚初始化,则直接返回
if (snowflake_work_id_flag and snowflake_data_center_id_flag) then
    return string.format(
            '{"%s": %d, "%s": %d}',
            'workId', return_work_id,
            'dataCenterId', return_data_center_id)
end

--- 获取workId和dataCenterId
local snowflake_work_id = tonumber(redis.call('get', snowflake_work_id_key))
local snowflake_data_center_id = tonumber(redis.call('get', snowflake_data_center_id_key))

--- 处理workId和dataCenterId达到上线的情况
if (snowflake_work_id >= max_work_id) then
    ---归零，循环使用
    redis.call('set', snowflake_work_id_key, 0)
    return_work_id = 0
    if (snowflake_data_center_id >= max_data_center_id) then
        ---归零，循环使用
        redis.call('set', snowflake_data_center_id_key, 0)
        return_data_center_id = 0
    else
        --- dataCenterId自增
        return_data_center_id = redis.call('incr', snowflake_data_center_id_key)
    end
else
    ---- workId自增
    return_work_id = redis.call('incr',snowflake_work_id_key)
end

return string.format(
        '{"%s": %d, "%s": %d}',
        'workId', return_work_id,
        'dataCenterId', return_data_center_id
		)
```

**脚本逻辑说明**

1. **首次启动**：如果 Redis 中不存在 `workId` 和 `dataCenterId` 的 key，则初始化为 0 并直接返回 `(0, 0)`。
2. **正常分配**：如果 `workId` 未达到上限（31），则对 `workId` 执行自增并返回。
3. **workId 达到上限**：将 `workId` 归零重置；如果 `dataCenterId` 也达到上限则一同归零，否则对 `dataCenterId` 执行自增。
4. **循环使用**：当所有 ID 组合用尽后，从 `(0, 0)` 重新开始循环分配。

### 3.7 SnowflakeIdGenerator（雪花算法 ID 生成器）

完整的雪花算法 ID 生成器实现，支持三种构造方式：

- **【推荐】通过 Redis 分配的 WorkDataCenterId 构造**：分布式环境优先使用，保证唯一不重复。
- **通过指定 IP 地址构造**：基于本地 MAC 地址 + 进程 PID 自动生成。
- **手动指定 workerId + dataCenterId**：适用于测试或特殊场景。

```java
@Slf4j
public class SnowflakeIdGenerator {

    /**
     * 基准时间：2010-11-04 09:42:54
     * 一旦确定不能修改，保证ID长度与趋势递增
     */
    private static final long BASIS_TIME = 1288834974657L;

    // ============================== 核心位配置 ==============================
    /** 序列号所占位数 12bit，每毫秒支持 4096 个ID */
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
     */
    public SnowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
        if (Objects.nonNull(workDataCenterId.getDataCenterId())) {
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

    private void initLog() {
        if (log.isDebugEnabled()) {
            log.debug("雪花算法生成器初始化完成 → 数据中心ID:{} 机器ID:{}", this.datacenterId, this.workerId);
        }
    }

    // ============================== 自动生成机器ID/数据中心ID ==============================

    /**
     * 根据 数据中心ID + 进程PID 生成机器ID，保证单机多进程不重复
     */
    protected long getMaxWorkerId(long datacenterId) {
        StringBuilder mPid = new StringBuilder();
        mPid.append(datacenterId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (StringUtils.isNotBlank(name)) {
            mPid.append(name.split("@")[0]);
        }
        return (mPid.toString().hashCode() & 0xffff) % (MAX_WORKER_ID + 1);
    }

    /**
     * 根据本机MAC地址生成数据中心ID，保证多机器不重复
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
     * 公共逻辑：时钟回拨处理 + 序列号生成
     */
    private long getBase() {
        int maxOffset = 5;
        long timestamp = timeGen();

        // 处理时钟回拨（服务器时间同步异常）
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

        // 序列号自增逻辑
        if (lastTimestamp == timestamp) {
            // 同一毫秒内 → 序列号+1
            final long sequenceMask = ~(-1L << sequenceBits);
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // 序列号耗尽 → 等待下一毫秒
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
     * 生成下一个分布式ID，加锁保证线程安全
     */
    public synchronized long nextId() {
        long timestamp = getBase();

        // 位移计算
        final long datacenterIdShift = sequenceBits + WORKER_ID_BITS;
        final long timestampLeftShift = sequenceBits + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

        // 拼接最终ID：时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return ((timestamp - BASIS_TIME) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << sequenceBits)
                | sequence;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return SystemClock.now();
    }
}
```

**关键设计说明**

- **基准时间 `BASIS_TIME`**：值为 `1288834974657L`（2010-11-04 09:42:54），时间戳部分存储的是当前时间与基准时间的差值，而非绝对时间戳，这样可以用更少的位数表示更长的时间跨度。
- **时钟回拨处理**：当检测到时钟回拨时，如果偏移量在 5ms 以内，会等待 2 倍偏移时间后重试；超过 5ms 则直接抛出异常拒绝生成 ID。
- **序列号初始化优化**：不同毫秒时序列号不从 0 开始，而是随机取 1 或 2（`ThreadLocalRandom.current().nextLong(1, 3)`），这是相对 MyBatis-Plus 原始实现的优化点，可以一定程度上避免低位 ID 冲突。
- **时间获取**：使用 Hutool 的 `SystemClock.now()` 而非 `System.currentTimeMillis()`，通过缓存时间戳减少系统调用，提高性能。

---

## 四、总结对比

- 本组件对 MyBatis-Plus 的雪花算法进行了改造优化，通过 Redis Lua 脚本分配 `workerId` 和 `dataCenterId`，解决了 K8s 环境下基于本地 MAC 地址和 PID 生成机器标识导致的 ID 重复问题。
- 在构建 `SnowflakeIdGenerator` 时，如果通过 Lua 脚本加载获取 `WorkDataCenterId` 失败，则回退采用 MyBatis-Plus 原始的本地生成策略作为兜底方案。
- `nextId()` 方法是获取分布式 ID 的入口，其内部 `getBase()` 负责时钟回拨处理和序列号生成，最终 ID 由 **时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分** 四个部分通过位运算拼接而成。
- 组件通过 Spring Boot 自动装配机制加载，接入方只需引入依赖并配置 Redis 即可使用，无需额外配置。

### 4.1 美团 Leaf

| 特性           | 说明                                         |
| -------------- | -------------------------------------------- |
| Leaf-segment   | 基于数据库自增ID，适合低并发场景             |
| Leaf-snowflake | 基于ZooKeeper分配workerId，保证唯一性        |
| 本地缓存       | 从ZK获取workerId后在本地文件缓存，提高可用性 |

### 4.2 百度 UidGenerator

| 特性          | 说明                                 |
| ------------- | ------------------------------------ |
| RingBuffer    | 使用环形缓冲区缓存预生成的UID        |
| 借用未来时间  | 通过借用未来时间解决sequence并发限制 |
| CacheLine填充 | 避免CPU伪共享问题                    |
| 性能          | 单机QPS可达600万                     |
| workerId策略  | 数据库分配，支持复用策略             |

---

## 
