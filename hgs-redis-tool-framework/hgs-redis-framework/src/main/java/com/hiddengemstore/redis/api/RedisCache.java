package com.hiddengemstore.redis.api;

import com.hiddengemstore.redis.internal.RedisKeyBuild;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 缓存操作统一接口，封装了对 Redis 各种数据结构（String、Hash、List、Set、SortedSet）的常用操作。
 * <p>提供统一的缓存访问抽象，屏蔽底层 {@link RedisTemplate} 的细节。所有缓存值均以 JSON 字符串形式存储，
 * 读取时自动反序列化为目标类型。默认过期时间单位为秒（{@link java.util.concurrent.TimeUnit#SECONDS}）。
 * 支持泛型反序列化、过期时间设置、缓存穿透保护（Supplier 回调）等能力。</p>
 *
 * @author ZhaoJH
 */
public interface RedisCache {
    /**
     * 获取字符串值并反序列化为指定类型
     * @param redisKeyBuild 缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 反序列化后的对象，key 不存在时返回 {@code null}
     */
    <T> T get(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 获取字符串值并反序列化为指定类型，若缓存不存在则通过 Supplier 回调加载并写入缓存
     * @param redisKeyBuild 缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param supplier      缓存未命中时执行的数据加载逻辑
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     * @param <T>           目标类型泛型
     * @return 反序列化后的对象
     */
    <T> T get(RedisKeyBuild redisKeyBuild, Class<T> clazz, Supplier<T> supplier, long ttl, TimeUnit timeUnit);

    /**
     * 返回 key 中字符串值的子字符串
     * @param redisKeyBuild 缓存 key 构建器
     * @param start         开始偏移量（包含）
     * @param end           结束偏移量（包含），-1 表示到字符串末尾
     * @return 截取的子字符串
     */
    String getRange(RedisKeyBuild redisKeyBuild, long start, long end);

    /**
     * 获取字符串值并反序列化为 List 集合
     * @param redisKeyBuild 缓存 key 构建器
     * @param clazz         集合元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 反序列化后的 List，key 不存在时返回空集合
     */
    <T> List<T> getValueIsList(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 获取字符串值并反序列化为 List 集合，若缓存不存在则通过 Supplier 回调加载并写入缓存
     * @param redisKeyBuild 缓存 key 构建器
     * @param clazz         集合元素类型的 Class 对象
     * @param supplier      缓存未命中时执行的数据加载逻辑
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     * @param <T>           元素类型泛型
     * @return 反序列化后的 List；缓存未命中时调用 supplier 加载，若 supplier 返回 {@code null} 或空集合则返回 {@code null}
     */
    <T> List<T> getValueIsList(RedisKeyBuild redisKeyBuild, Class<T> clazz, Supplier<List<T>> supplier, long ttl, TimeUnit timeUnit);


    /**
     * 批量获取多个 key 对应的字符串值，内部通过双重 multiGet 优化以降低空结果概率
     * @param keyList 缓存 key 构建器列表
     * @return 各 key 对应的值列表，key 不存在时对应位置为 {@code null}
     */
    List<String> getKeys(List<RedisKeyBuild> keyList);

    /**
     * 判断 key 是否存在
     * @param redisKeyBuild 缓存 key 构建器
     * @return {@code true} 表示存在，{@code false} 表示不存在
     */
    Boolean hasKey(RedisKeyBuild redisKeyBuild);

    /**
     * 删除指定 key
     * @param redisKeyBuild 缓存 key 构建器
     */
    void del(RedisKeyBuild redisKeyBuild);


    /**
     * 批量删除多个 key
     * @param keys 缓存 key 构建器集合
     */
    void del(Collection<RedisKeyBuild> keys);

    /**
     * 设置 key 的过期时间
     *
     * @param redisKeyBuild 缓存 key 构建器
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     */
    void expire(RedisKeyBuild redisKeyBuild, long ttl, TimeUnit timeUnit);

    /**
     * 获取 key 的剩余过期时间（单位：秒）
     * @param redisKeyBuild 缓存 key 构建器
     * @return 剩余过期时间（秒），-1 表示永不过期，-2 表示 key 不存在
     */
    Long getExpire(RedisKeyBuild redisKeyBuild);

    /**
     * 获取 key 的剩余过期时间（指定时间单位）
     * @param redisKeyBuild 缓存 key 构建器
     * @param timeUnit      返回值的时间单位
     * @return 剩余过期时间，-1 表示永不过期，-2 表示 key 不存在
     */
    Long getExpire(RedisKeyBuild redisKeyBuild, TimeUnit timeUnit);

    /**
     * 查找匹配模式的所有 key
     * @param pattern key 匹配模式（支持通配符 *、? 等）
     * @return 匹配的 key 集合
     */
    Set<String> keys(String pattern);

    /**
     * 将当前数据库的 key 移动到指定数据库
     * @param redisKeyBuild 缓存 key 构建器
     * @param dbIndex       目标数据库索引
     * @return {@code true} 移动成功，{@code false} key 不存在或目标数据库中已存在同名 key
     */
    Boolean move(RedisKeyBuild redisKeyBuild, int dbIndex);

    /**
     * 移除 key 的过期时间，使其持久化
     * @param redisKeyBuild 缓存 key 构建器
     * @return {@code true} 移除成功，{@code false} key 不存在或没有设置过期时间
     */
    Boolean persist(RedisKeyBuild redisKeyBuild);

    /**
     * 从当前数据库中随机返回一个 key
     * @return 随机的 key 名称，数据库为空时返回 {@code null}
     */
    String randomKey();

    /**
     * 修改 key 的名称
     * @param oldKey 原缓存 key 构建器
     * @param newKey 新缓存 key 构建器
     */
    void rename(RedisKeyBuild oldKey, RedisKeyBuild newKey);

    /**
     * 仅当 newKey 不存在时，将 oldKey 改名为 newKey
     * @param oldKey 原缓存 key 构建器
     * @param newKey 新缓存 key 构建器
     * @return {@code true} 改名成功，{@code false} newKey 已存在
     */
    Boolean renameIfAbsent(RedisKeyBuild oldKey, RedisKeyBuild newKey);

    /**
     * 返回 key 所储存的值的数据类型
     * @param redisKeyBuild 缓存 key 构建器
     * @return 数据类型枚举（STRING、LIST、SET、ZSET、HASH 等），key 不存在时返回 {@code NONE}
     */
    DataType type(RedisKeyBuild redisKeyBuild);

    /**
     * 设置缓存值（不过期）
     * @param redisKeyBuild 缓存 key 构建器
     * @param object        缓存对象，将被序列化存储
     */
    void set(RedisKeyBuild redisKeyBuild, Object object);

    /**
     * 设置缓存值并指定过期时间（单位：秒）
     * @param redisKeyBuild 缓存 key 构建器
     * @param object        缓存对象
     * @param ttl           过期时间（秒）
     */
    void set(RedisKeyBuild redisKeyBuild, Object object, long ttl);

    /**
     * 设置缓存值并指定过期时间和时间单位
     * @param redisKeyBuild 缓存 key 构建器
     * @param object        缓存对象
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     */
    void set(RedisKeyBuild redisKeyBuild, Object object, long ttl, TimeUnit timeUnit);

    /**
     * 仅当 key 不存在时设置缓存值（SETNX 语义）
     * @param redisKeyBuild 缓存 key 构建器
     * @param object        缓存对象
     * @return {@code true} 设置成功（key 之前不存在），{@code false} key 已存在
     */
    boolean setIfAbsent(RedisKeyBuild redisKeyBuild, Object object);

    /**
     * 仅当 key 不存在时设置缓存值，并指定过期时间
     * @param redisKeyBuild 缓存 key 构建器
     * @param object        缓存对象
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     * @return {@code true} 设置成功（key 之前不存在），{@code false} key 已存在
     */
    boolean setIfAbsent(RedisKeyBuild redisKeyBuild, Object object, long ttl, TimeUnit timeUnit);

    /**
     * 获取字符串值的长度
     * @param redisKeyBuild 缓存 key 构建器
     * @return 字符串长度，key 不存在时返回 0
     */
    Long size(RedisKeyBuild redisKeyBuild);

    /**
     * 批量设置多个 key-value 对
     * @param map key-value 映射，key 为缓存 key 构建器，value 为缓存对象
     */
    void multiSet(Map<RedisKeyBuild, ?> map);

    /**
     * 批量设置多个 key-value 对，当且仅当所有给定 key 都不存在时才执行
     * @param map key-value 映射
     * @return {@code true} 全部设置成功，{@code false} 存在任一 key 已存在
     */
    boolean multiSetIfAbsent(Map<RedisKeyBuild, ?> map);

    /**
     * 对整数值进行自增操作，负数则为自减
     * @param redisKeyBuild 缓存 key 构建器
     * @param increment     增量（可为负数）
     * @return 操作后的值
     */
    Long incrBy(RedisKeyBuild redisKeyBuild, long increment);

    /**
     * 对浮点数值进行自增操作，负数则为自减
     * @param redisKeyBuild 缓存 key 构建器
     * @param increment     增量（可为负数）
     * @return 操作后的值
     */
    Double incrByDouble(RedisKeyBuild redisKeyBuild, double increment);

    /**
     * 将值追加到已有字符串的末尾，若 key 不存在则创建
     * @param redisKeyBuild 缓存 key 构建器
     * @param value         要追加的字符串
     * @return 追加后字符串的总长度
     */
    Integer append(RedisKeyBuild redisKeyBuild, String value);

    /* ------------------- Hash 相关操作 ------------------------- */

    /**
     * 向 Hash 中放入一个键值对
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param value         Hash 中的字段值
     */
    void putHash(RedisKeyBuild redisKeyBuild, String hashKey, Object value);

    /**
     * 向 Hash 中放入一个键值对，并设置过期时间（单位：秒）
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param value         Hash 中的字段值
     * @param ttl           过期时间（秒）
     */
    void putHash(RedisKeyBuild redisKeyBuild, String hashKey, Object value, long ttl);

    /**
     * 向 Hash 中放入一个键值对，并设置过期时间和时间单位
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param value         Hash 中的字段值
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     */
    void putHash(RedisKeyBuild redisKeyBuild, String hashKey, Object value, long ttl, TimeUnit timeUnit);

    /**
     * 批量向 Hash 中放入多个键值对
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param map           字段名-字段值映射
     */
    void putHash(RedisKeyBuild redisKeyBuild, Map<String, ?> map);

    /**
     * 批量向 Hash 中放入多个键值对，并设置过期时间（单位：秒）
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param map           字段名-字段值映射
     * @param ttl           过期时间（秒）
     */
    void putHash(RedisKeyBuild redisKeyBuild, Map<String, ?> map, long ttl);

    /**
     * 批量向 Hash 中放入多个键值对，并设置过期时间和时间单位
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param map           字段名-字段值映射
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     */
    void putHash(RedisKeyBuild redisKeyBuild, Map<String, ?> map, long ttl, TimeUnit timeUnit);

    /**
     * 仅当 Hash 中指定字段不存在时才设置值
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param value         字段值
     * @return {@code true} 设置成功（字段之前不存在），{@code false} 字段已存在
     */
    Boolean putHashIfAbsent(RedisKeyBuild redisKeyBuild, String hashKey, Object value);

    /**
     * 从 Hash 中获取指定字段的值并反序列化为指定类型
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 反序列化后的对象，字段不存在时返回 {@code null}
     */
    @SuppressWarnings("all")
    <T> T getForHash(RedisKeyBuild redisKeyBuild, String hashKey, Class<T> clazz);

    /**
     * 从 Hash 中获取指定字段的值并反序列化为 List 集合
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param clazz         集合元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 反序列化后的 List，字段不存在时返回 {@code null}
     */
    <T> List<T> getValueIsListForHash(RedisKeyBuild redisKeyBuild, String hashKey, Class<T> clazz);

    /**
     * 从 Hash 中批量获取多个字段的值并反序列化
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKeys      要获取的字段名列表
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 各字段对应的值列表，字段不存在时对应位置为 {@code null}
     */
    <T> List<T> multiGetForHash(RedisKeyBuild redisKeyBuild, List<String> hashKeys, Class<T> clazz);

    /**
     * 获取 Hash 中所有字段的值并反序列化为 List。
     * <p>注意：当 Hash 中数据量较大时，可能导致性能问题，请谨慎使用。</p>
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 所有字段值的 List
     */
    <T> List<T> getAllForHash(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 获取 Hash 中所有字段和值并反序列化为 Map。
     * <p>注意：当 Hash 中数据量较大时，可能导致性能问题，请谨慎使用。</p>
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param clazz         值类型的 Class 对象
     * @param <T>           值类型泛型
     * @return 字段名到字段值的映射
     */
    <T> Map<String, T> getAllMapForHash(RedisKeyBuild redisKeyBuild, Class<T> clazz);
    /**
     * 判断 Hash 中指定字段是否存在
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @return {@code true} 字段存在，{@code false} 字段不存在
     */
    Boolean hasKeyForHash(RedisKeyBuild redisKeyBuild, String hashKey);

    /**
     * 删除 Hash 中指定字段
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @return 成功删除的字段数量（0 或 1）
     */
    Long delForHash(RedisKeyBuild redisKeyBuild, String hashKey);

    /**
     * 批量删除 Hash 中的多个字段
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKeys      要删除的字段名集合
     * @return 成功删除的字段数量
     */
    Long delForHash(RedisKeyBuild redisKeyBuild, Collection<String> hashKeys);

    /**
     * 对 Hash 中指定字段的整数值进行自增操作
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param increment     增量（可为负数）
     * @return 操作后的值
     */
    Long incrByForHash(RedisKeyBuild redisKeyBuild, String hashKey, long increment);

    /**
     * 对 Hash 中指定字段的浮点数值进行自增操作
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @param hashKey       Hash 中的字段名
     * @param delta         增量（可为负数）
     * @return 操作后的值
     */
    Double incrByDoubleForHash(RedisKeyBuild redisKeyBuild, String hashKey, double delta);

    /**
     * 获取 Hash 中所有字段名
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @return 所有字段名的集合
     */
    Set<String> hashKeysForHash(RedisKeyBuild redisKeyBuild);

    /**
     * 获取 Hash 中字段的数量
     * @param redisKeyBuild Hash key 的缓存 key 构建器
     * @return 字段数量，key 不存在时返回 0
     */
    Long sizeForHash(RedisKeyBuild redisKeyBuild);

    /* ------------------------ List 相关操作 ---------------------------- */

    /**
     * 通过索引获取列表中的元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param index         索引位置（0 表示第一个元素，-1 表示最后一个元素）
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 反序列化后的元素，索引越界时返回 {@code null}
     */
    <T> T indexForList(RedisKeyBuild redisKeyBuild, long index, Class<T> clazz);

    /**
     * 从列表左侧（头部）插入元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param value         要插入的元素
     * @return 插入后列表的长度
     */
    Long leftPushForList(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 从列表左侧（头部）批量插入多个元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param valueList     要插入的元素列表
     * @return 插入后列表的长度
     */
    Long leftPushAllForList(RedisKeyBuild redisKeyBuild, List<?> valueList);

    /**
     * 仅当列表存在时，从列表左侧（头部）插入元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param value         要插入的元素
     * @return 插入后列表的长度，列表不存在时返回 0
     */
    Long leftPushIfPresentForList(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 在列表中指定元素（pivot）的左侧插入新元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param pivot         参照元素
     * @param value         要插入的元素
     * @return 插入后列表的长度，pivot 不存在时返回 -1
     */
    Long leftPushForList(RedisKeyBuild redisKeyBuild, Object pivot, Object value);

    /**
     * 从列表右侧（尾部）插入元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param value         要插入的元素
     * @return 插入后列表的长度
     */
    Long rightPushForList(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 从列表右侧（尾部）批量插入多个元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param valueList     要插入的元素列表
     * @return 插入后列表的长度
     */
    Long rightPushAllForList(RedisKeyBuild redisKeyBuild, List<Object> valueList);

    /**
     * 仅当列表存在时，从列表右侧（尾部）插入元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param value         要插入的元素
     * @return 插入后列表的长度，列表不存在时返回 0
     */
    Long rightPushIfPresentForList(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 在列表中指定元素（pivot）的右侧插入新元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param pivot         参照元素
     * @param value         要插入的元素
     * @return 插入后列表的长度，pivot 不存在时返回 -1
     */
    Long rightPushForList(RedisKeyBuild redisKeyBuild, Object pivot, Object value);

    /**
     * 通过索引设置列表元素的值
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param index         索引位置
     * @param value         要设置的值
     */
    void setForList(RedisKeyBuild redisKeyBuild, long index, Object value);

    /**
     * 移除并返回列表的第一个元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 移除的元素，列表为空时返回 {@code null}
     */
    <T> T leftPopForList(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 移除并返回列表的第一个元素（阻塞模式），若列表为空则阻塞等待直到超时或有新元素加入
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param timeout       最长等待时间
     * @param unit          等待时间单位
     * @param <T>           目标类型泛型
     * @return 移除的元素，超时后仍无元素返回 {@code null}
     */
    <T> T leftPopBlockForList(RedisKeyBuild redisKeyBuild, Class<T> clazz, long timeout, TimeUnit unit);

    /**
     * 移除并返回列表的最后一个元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 移除的元素，列表为空时返回 {@code null}
     */
    <T> T rightPopForList(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 移除并返回列表的最后一个元素（阻塞模式），若列表为空则阻塞等待直到超时或有新元素加入
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param timeout       最长等待时间
     * @param unit          等待时间单位
     * @param <T>           目标类型泛型
     * @return 移除的元素，超时后仍无元素返回 {@code null}
     */
    <T> T rightPopBlockForList(RedisKeyBuild redisKeyBuild, Class<T> clazz, long timeout, TimeUnit unit);

    /**
     * 移除源列表的最后一个元素，并将其添加到目标列表的头部
     * @param sourceKey      源列表的缓存 key 构建器
     * @param destinationKey 目标列表的缓存 key 构建器
     * @param clazz          目标类型的 Class 对象
     * @param <T>            目标类型泛型
     * @return 被移动的元素
     */
    <T> T rightPopAndLeftPushForList(RedisKeyBuild sourceKey, RedisKeyBuild destinationKey, Class<T> clazz);

    /**
     * 移除源列表的最后一个元素，并将其添加到目标列表的头部（阻塞模式），
     * 若源列表为空则阻塞等待直到超时或有新元素加入
     * @param sourceKey      源列表的缓存 key 构建器
     * @param destinationKey 目标列表的缓存 key 构建器
     * @param clazz          目标类型的 Class 对象
     * @param timeout        最长等待时间
     * @param unit           等待时间单位
     * @param <T>            目标类型泛型
     * @return 被移动的元素，超时后仍无元素返回 {@code null}
     */
    <T> T rightPopBlockAndLeftPushForList(RedisKeyBuild sourceKey, RedisKeyBuild destinationKey, Class<T> clazz, long timeout, TimeUnit unit);

    /**
     * 获取列表中所有元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 所有元素的 List，列表不存在时返回空列表
     */
    <T> List<T> getAllForList(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 获取列表指定范围内的元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param start         开始位置（0 表示第一个元素）
     * @param end           结束位置（-1 表示最后一个元素）
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 指定范围内的元素列表
     */
    <T> List<T> rangeForList(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz);


    /**
     * 从列表中删除指定数量的匹配元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param index         删除策略：=0 删除所有匹配元素；&gt;0 从头部开始删除前 N 个匹配元素；&lt;0 从尾部开始删除前 |N| 个匹配元素
     * @param value         要删除的元素值
     * @return 实际删除的元素数量
     */
    Long removeForList(RedisKeyBuild redisKeyBuild, long index, Object value);


    /**
     * 裁剪列表，只保留指定范围内的元素
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @param start         开始位置（包含）
     * @param end           结束位置（包含）
     */
    void trimForList(RedisKeyBuild redisKeyBuild, long start, long end);

    /**
     * 获取列表长度
     * @param redisKeyBuild List key 的缓存 key 构建器
     * @return 列表长度，key 不存在时返回 0
     */
    Long lenForList(RedisKeyBuild redisKeyBuild);


    /* -------------------- Set 相关操作 -------------------------- */

    /**
     * 向集合中添加元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param value         要添加的元素
     * @return 成功添加的元素数量（0 表示元素已存在）
     */
    Long addForSet(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 批量向集合中添加元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param values        要添加的元素列表
     * @return 成功添加的元素数量
     */
    Long addForSet(RedisKeyBuild redisKeyBuild, List<?> values);

    /**
     * 从集合中移除元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param value         要移除的元素
     * @return 成功移除的元素数量（0 表示元素不存在）
     */
    Long removeForSet(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 批量从集合中移除元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param values        要移除的元素列表
     * @return 成功移除的元素数量
     */
    Long removeForSet(RedisKeyBuild redisKeyBuild, List<?> values);

    /**
     * 移除并返回集合中的一个随机元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 随机弹出的元素，集合为空时返回 {@code null}
     */
    <T> T popForSet(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 将元素从一个集合移动到另一个集合
     * @param redisKeyBuild      源 Set key 的缓存 key 构建器
     * @param value              要移动的元素
     * @param destRedisKeyBuild  目标 Set key 的缓存 key 构建器
     * @return {@code true} 移动成功，{@code false} 元素不在源集合中
     */
    boolean moveForSet(RedisKeyBuild redisKeyBuild, Object value, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取集合的大小
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @return 集合元素数量，key 不存在时返回 0
     */
    Long sizeForSet(RedisKeyBuild redisKeyBuild);

    /**
     * 判断集合是否包含指定元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param value         要判断的元素
     * @return {@code true} 包含该元素，{@code false} 不包含
     */
    Boolean isMemberForSet(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 获取两个集合的交集
     * @param redisKeyBuild      第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 Set key 的缓存 key 构建器
     * @param clazz              元素类型的 Class 对象
     * @param <T>                元素类型泛型
     * @return 交集结果
     */
    <T> Set<T> intersectForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, Class<T> clazz);

    /**
     * 获取一个集合与多个集合的交集
     * @param redisKeyBuild       第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 Set key 的缓存 key 构建器集合
     * @param clazz               元素类型的 Class 对象
     * @param <T>                 元素类型泛型
     * @return 交集结果
     */
    <T> Set<T> intersectForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, Class<T> clazz);

    /**
     * 获取两个集合的交集并存储到目标集合中
     * @param redisKeyBuild      第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 Set key 的缓存 key 构建器
     * @param destRedisKeyBuild  目标 Set key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long intersectAndStoreForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取一个集合与多个集合的交集并存储到目标集合中
     * @param redisKeyBuild       第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 Set key 的缓存 key 构建器集合
     * @param destRedisKeyBuild   目标 Set key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long intersectAndStoreForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取两个集合的并集
     * @param redisKeyBuild      第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 Set key 的缓存 key 构建器
     * @param clazz              元素类型的 Class 对象
     * @param <T>                元素类型泛型
     * @return 并集结果
     */
    <T> Set<T> unionForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, Class<T> clazz);

    /**
     * 获取一个集合与多个集合的并集
     * @param redisKeyBuild       第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 Set key 的缓存 key 构建器集合
     * @param clazz               元素类型的 Class 对象
     * @param <T>                 元素类型泛型
     * @return 并集结果
     */
    <T> Set<T> unionForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, Class<T> clazz);

    /**
     * 获取两个集合的并集并存储到目标集合中
     * @param redisKeyBuild      第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 Set key 的缓存 key 构建器
     * @param destRedisKeyBuild  目标 Set key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long unionAndStoreForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取一个集合与多个集合的并集并存储到目标集合中
     * @param redisKeyBuild       第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 Set key 的缓存 key 构建器集合
     * @param destRedisKeyBuild   目标 Set key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long unionAndStoreForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取两个集合的差集（存在于第一个集合但不存在于第二个集合的元素）
     * @param redisKeyBuild      第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 Set key 的缓存 key 构建器
     * @param clazz              元素类型的 Class 对象
     * @param <T>                元素类型泛型
     * @return 差集结果
     */
    <T> Set<T> differenceForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, Class<T> clazz);

    /**
     * 获取一个集合与多个集合的差集
     * @param redisKeyBuild       第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 Set key 的缓存 key 构建器集合
     * @param clazz               元素类型的 Class 对象
     * @param <T>                 元素类型泛型
     * @return 差集结果
     */
    <T> Set<T> differenceForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, Class<T> clazz);

    /**
     * 获取两个集合的差集并存储到目标集合中
     * @param redisKeyBuild      第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 Set key 的缓存 key 构建器
     * @param destRedisKeyBuild  目标 Set key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long differenceForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取一个集合与多个集合的差集并存储到目标集合中
     * @param redisKeyBuild       第一个 Set key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 Set key 的缓存 key 构建器集合
     * @param destRedisKeyBuild   目标 Set key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long differenceForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取集合中所有元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 所有元素的集合
     */
    <T> Set<T> membersForSet(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 随机获取集合中的一个元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 随机元素，集合为空时返回 {@code null}
     */
    <T> T randomMemberForSet(RedisKeyBuild redisKeyBuild, Class<T> clazz);

    /**
     * 随机获取集合中的多个元素（可能重复）
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param count         要获取的元素数量
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 随机元素列表
     */
    <T> List<T> randomMembersForSet(RedisKeyBuild redisKeyBuild, long count, Class<T> clazz);

    /**
     * 随机获取集合中的多个不重复元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param count         要获取的元素数量（不能超过集合大小）
     * @param clazz         目标类型的 Class 对象
     * @param <T>           目标类型泛型
     * @return 不重复的随机元素集合
     */
    <T> Set<T> distinctRandomMembersForSet(RedisKeyBuild redisKeyBuild, long count, Class<T> clazz);

    /**
     * 使用游标遍历集合中的元素
     * @param redisKeyBuild Set key 的缓存 key 构建器
     * @param options       扫描选项（匹配模式、每次扫描数量等）
     * @return 游标对象，需要在使用后关闭
     */
    Cursor<String> scanForSet(RedisKeyBuild redisKeyBuild, ScanOptions options);



    /*------------------ SortedSet 相关操作 --------------------------------*/

    /**
     * 向有序集合中添加一个元素
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         要添加的元素
     * @param score         元素的分值
     */
    void addForSortedSet(RedisKeyBuild redisKeyBuild, Object value, Double score);

    /**
     * 向有序集合中添加一个元素，并设置过期时间（单位：秒）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         要添加的元素
     * @param score         元素的分值
     * @param ttl           过期时间（秒）
     */
    void addForSortedSet(RedisKeyBuild redisKeyBuild, Object value, Double score, long ttl);

    /**
     * 向有序集合中添加一个元素，并设置过期时间和时间单位
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         要添加的元素
     * @param score         元素的分值
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     */
    void addForSortedSet(RedisKeyBuild redisKeyBuild, Object value, Double score, long ttl, TimeUnit timeUnit);

    /**
     * 批量向有序集合中添加元素
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param map           元素-分值映射，key 如果是自定义对象类型需要重写 equals 和 hashCode 方法
     * @return 成功添加的元素数量
     */
    Long addForSortedSet(RedisKeyBuild redisKeyBuild, Map<?, Double> map);

    /**
     * 批量向有序集合中添加元素，并设置过期时间（单位：秒）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param map           元素-分值映射
     * @param ttl           过期时间（秒）
     * @return 成功添加的元素数量
     */
    Long addForSortedSet(RedisKeyBuild redisKeyBuild, Map<?, Double> map, long ttl);

    /**
     * 批量向有序集合中添加元素，并设置过期时间和时间单位
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param map           元素-分值映射，key 如果是自定义对象类型需要重写 equals 和 hashCode 方法
     * @param ttl           过期时间数值
     * @param timeUnit      过期时间单位
     * @return 成功添加的元素数量
     */
    Long addForSortedSet(RedisKeyBuild redisKeyBuild, Map<?, Double> map, long ttl, TimeUnit timeUnit);

    /**
     * 获取有序集合中指定范围的元素（按分值从小到大排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param start         开始下标（从 0 开始）
     * @param end           结束下标（包含，-1 表示到最后一个元素）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 指定范围内的元素集合
     */
    <T> Set<T> getRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz);

    /**
     * 获取有序集合中指定范围的元素（按分值从大到小排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param start         开始下标（从 0 开始）
     * @param end           结束下标（包含，-1 表示到最后一个元素）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 指定范围内的元素集合（逆序）
     */
    <T> Set<T> getReverseRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz);

    /**
     * 从有序集合中删除指定元素
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         要删除的元素
     * @return 成功删除的元素数量（0 或 1）
     */
    Long delForSortedSet(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 批量从有序集合中删除元素
     * @param redisKeyBuild   SortedSet key 的缓存 key 构建器
     * @param valueCollection 要删除的元素集合
     * @return 成功删除的元素数量
     */
    Long delForSortedSet(RedisKeyBuild redisKeyBuild, Collection<?> valueCollection);

    /**
     * 按下标范围删除有序集合中的元素
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param start         开始下标（包含）
     * @param end           结束下标（包含）
     * @return 成功删除的元素数量
     */
    Long delRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end);


    /**
     * 增加有序集合中元素的分值，并返回增加后的值
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         目标元素
     * @param delta         分值增量（可为负数）
     * @return 增加后的分值
     */
    Double incrementScoreForSortedSet(RedisKeyBuild redisKeyBuild, Object value, double delta);

    /**
     * 获取有序集合的元素总数
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @return 元素总数，key 不存在时返回 0
     */
    Long sizeForSortedSet(RedisKeyBuild redisKeyBuild);

    /**
     * 返回元素在有序集合中的排名（按分值从小到大排列，排名从 0 开始）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         目标元素
     * @return 排名，元素不存在时返回 {@code null}
     */
    Long rankForSortedSet(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 返回元素在有序集合中的排名（按分值从大到小排列，排名从 0 开始）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         目标元素
     * @return 排名，元素不存在时返回 {@code null}
     */
    Long reverseRankForSortedSet(RedisKeyBuild redisKeyBuild, Object value);


    /**
     * 获取有序集合中指定范围的元素及其分值（按分值从小到大排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param start         开始下标（从 0 开始）
     * @param end           结束下标（包含，-1 表示到最后一个元素）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 包含元素和分值的 TypedTuple 集合
     */
    <T> Set<ZSetOperations.TypedTuple<T>> rangeWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz);


    /**
     * 根据分值范围查询有序集合元素（按分值从小到大排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 符合分值范围的元素集合
     */
    <T> Set<T> rangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz);

    /**
     * 根据分值范围查询有序集合元素及其分值（按分值从小到大排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 包含元素和分值的 TypedTuple 集合
     */
    <T> Set<ZSetOperations.TypedTuple<T>> rangeByScoreWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz);


    /**
     * 根据分值范围查询有序集合元素及其分值，支持分页（按分值从小到大排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @param start         偏移量（从 0 开始）
     * @param end           返回的最大数量
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 包含元素和分值的 TypedTuple 集合
     */
    <T> Set<ZSetOperations.TypedTuple<T>> rangeByScoreWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max,
                                                                            long start, long end, Class<T> clazz);

    /**
     * 获取有序集合中指定范围的元素及其分值（按分值从大到小排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param start         开始下标（从 0 开始）
     * @param end           结束下标（包含，-1 表示到最后一个元素）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 包含元素和分值的 TypedTuple 集合（逆序）
     */
    <T> Set<ZSetOperations.TypedTuple<T>> reverseRangeWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz);

    /**
     * 根据分值范围查询有序集合元素（按分值从大到小排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 符合分值范围的元素集合（逆序）
     */
    <T> Set<T> reverseRangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz);

    /**
     * 根据分值范围查询有序集合元素及其分值（按分值从大到小排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 包含元素和分值的 TypedTuple 集合（逆序）
     */
    <T> Set<ZSetOperations.TypedTuple<T>> reverseRangeByScoreWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz);

    /**
     * 根据分值范围查询有序集合元素，支持分页（按分值从大到小排序）
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @param start         偏移量（从 0 开始）
     * @param end           返回的最大数量
     * @param clazz         元素类型的 Class 对象
     * @param <T>           元素类型泛型
     * @return 符合分值范围的元素集合（逆序）
     */
    <T> Set<T> reverseRangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, long start, long end, Class<T> clazz);

    /**
     * 统计有序集合中分值在指定范围内的元素数量
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @return 符合条件的元素数量
     */
    Long countForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max);

    /**
     * 获取有序集合的元素总数
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @return 元素总数，key 不存在时返回 0
     */
    Long zCardForSortedSet(RedisKeyBuild redisKeyBuild);

    /**
     * 获取有序集合中指定元素的分值
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param value         目标元素
     * @return 元素的分值，元素不存在时返回 {@code null}
     */
    Double scoreByValueForSortedSet(RedisKeyBuild redisKeyBuild, Object value);

    /**
     * 按下标范围移除有序集合中的元素
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param start         开始下标（包含）
     * @param end           结束下标（包含）
     * @return 成功移除的元素数量
     */
    Long removeRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end);

    /**
     * 按分值范围移除有序集合中的元素
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param min           最小分值（包含）
     * @param max           最大分值（包含）
     * @return 成功移除的元素数量
     */
    Long removeRangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max);

    /**
     * 获取两个有序集合的并集并存储到目标集合中（分值相加）
     * @param redisKeyBuild      第一个 SortedSet key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 SortedSet key 的缓存 key 构建器
     * @param destRedisKeyBuild  目标 SortedSet key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long unionAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取一个有序集合与多个有序集合的并集并存储到目标集合中（分值相加）
     * @param redisKeyBuild       第一个 SortedSet key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 SortedSet key 的缓存 key 构建器集合
     * @param destRedisKeyBuild   目标 SortedSet key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long unionAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取两个有序集合的交集并存储到目标集合中（分值取最小值）
     * @param redisKeyBuild      第一个 SortedSet key 的缓存 key 构建器
     * @param otherRedisKeyBuild 第二个 SortedSet key 的缓存 key 构建器
     * @param destRedisKeyBuild  目标 SortedSet key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long intersectAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild);

    /**
     * 获取一个有序集合与多个有序集合的交集并存储到目标集合中（分值取最小值）
     * @param redisKeyBuild       第一个 SortedSet key 的缓存 key 构建器
     * @param otherRedisKeyBuilds 其他 SortedSet key 的缓存 key 构建器集合
     * @param destRedisKeyBuild   目标 SortedSet key 的缓存 key 构建器
     * @return 目标集合的元素数量
     */
    Long intersectAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild);

    /**
     * 使用游标遍历有序集合中的元素及其分值
     * @param redisKeyBuild SortedSet key 的缓存 key 构建器
     * @param options       扫描选项（匹配模式、每次扫描数量等）
     * @return 游标对象，需要在使用后关闭
     */
    Cursor<ZSetOperations.TypedTuple<String>> scanForSortedSet(RedisKeyBuild redisKeyBuild, ScanOptions options);

    /**
     * 根据泛型类型获取缓存值（内部方法，不对外使用）
     * @param redisKeyBuild     缓存 key 构建器
     * @param genericReturnType 泛型返回类型
     * @param <T>               目标类型泛型
     * @return 反序列化后的对象
     */
    <T> T getByType(RedisKeyBuild redisKeyBuild, Type genericReturnType);

    /**
     * 获取底层 RedisTemplate 实例（实际类型为 {@link org.springframework.data.redis.core.StringRedisTemplate}）
     * 由于StringRedisTemplate继承自RedisTemplate<String,String>，所以实际返回类型为RedisTemplate<String,String>
     * @return RedisTemplate 实例
     */
    RedisTemplate<String,String> getInstance();
}
