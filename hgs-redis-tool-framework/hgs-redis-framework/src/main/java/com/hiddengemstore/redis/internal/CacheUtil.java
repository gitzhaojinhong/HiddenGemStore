package com.hiddengemstore.redis.internal;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.util.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 缓存对象映射工具，提供参数校验、泛型类型构建、集合优化等通用能力。
 * <p>配合 {@link RedisCacheImpl} 使用，负责 key 合法性检查、fastjson 泛型类型构建、
 * 以及 Redis 批量查询结果的空值安全处理。</p>
 * @author : ZhaoJH
 */
public class CacheUtil {
    /** 默认过期时间单位：秒 */
    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * 使用 FastJSON 的 `ParameterizedTypeImpl` 构建泛型类型
     * 用于支持如 `List<User>` 这类带泛型的反序列化
     * 适用场景：
     * - `buildType(List.class, User.class)` → `List<User>`
     * - `buildType(List.class, String.class)` → `List<String>`
     * - `buildType(User.class)` → `User`
     * @param types 按层级顺序排列的泛型类型数组
     * @return 构建好的 ParameterizedType，可用于 fastjson 反序列化
     */
    public static Type buildType(Type... types) {
        ParameterizedTypeImpl beforeType = null;
        if (types != null && types.length > 0) {
            if (types.length == 1) {
                return new ParameterizedTypeImpl(new Type[]{null}, null, types[0]);
            }
            for (int i = types.length - 1; i > 0; i--) {
                beforeType = new ParameterizedTypeImpl(new Type[]{beforeType == null ? types[i] : beforeType}, null, types[i - 1]);
            }
        }
        return beforeType;
    }
    /**
     * 检查 Key 是否为空或空的字符串
     * @param key Redis Key
     */
    public static void checkNotBlank(String... key) {
        for (String s : key) {
            if (StrUtil.isEmpty(s)) {
                throw new RuntimeException("请求参数缺失");
            }
        }
    }

    /**
     * 检查 redisKeyBuild 中的key是否为空或空的字符串
     * @param redisKeyBuild key包装
     */
    public static void checkNotBlank(RedisKeyBuild redisKeyBuild) {
        if (StrUtil.isEmpty(redisKeyBuild.getRelKey())) {
            throw new RuntimeException("请求参数缺失");
        }
    }

    /**
     * 检查 list 是否为空或空的字符串
     * @param list 集合
     */
    public static void checkNotBlank(Collection<String> list) {
        for (String s : list) {
            if (StrUtil.isEmpty(s)) {
                throw new RuntimeException("请求参数缺失");
            }
        }
    }

    /**
     * 检查 list 是否为空或空的字符串
     * @param list key集合
     */
    public static void checkNotEmpty(Collection<?> list) {
        for (Object o : list) {
            if (o == null) {
                throw new RuntimeException("请求参数缺失");
            }
        }
    }

    /**
     * 检查 object 是否为空
     * @param object 待检查的对象
     */
    public static void checkNotEmpty(Object object) {
        if (isEmpty(object)) {
            throw new RuntimeException("请求参数缺失");
        }
    }

    /**
     * 判断 object 是否为空
     */
    public static boolean isEmpty(Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof String) {
            return StrUtil.isEmpty((String) object);
        }
        if (object instanceof Collection) {
            return ((Collection<?>) object).isEmpty();
        }
        return false;
    }

    /**
     * 从 RedisKeyBuild 集合中提取所有实际 Redis key
     * @param list RedisKeyBuild 集合
     * @return 实际 key 字符串列表
     */
    public static List<String> getBatchKey(Collection<RedisKeyBuild> list){
        return list.stream().map(RedisKeyBuild::getRelKey).collect(Collectors.toList());
    }

    /**
     * 优化 Redis multiGet 返回的列表，将 null 或首元素为 null 的列表转换为空集合，避免下游空指针
     * @param list Redis multiGet 返回的原始列表
     * @param <T>  元素类型
     * @return 安全的非 null 列表，若原始数据无效则返回空集合
     */
    public static <T> List<T> optimizeRedisList(List<T> list){
        if (Objects.isNull(list)) {
            return new ArrayList<>();
        }
        if (list.isEmpty() || Objects.isNull(list.get(0))) {
            return new ArrayList<>();
        }
        return list;
    }

    /**
     * 检查 Redis multiGet 返回的列表是否为空或无效（null、空列表、首元素为 null）
     * @param list Redis multiGet 返回的原始列表
     * @return {@code true} 表示列表为空或无效
     */
    public static boolean checkRedisListIsEmpty(List<?> list){
        if (Objects.isNull(list)) {
            return true;
        }
        return list.isEmpty() || Objects.isNull(list.get(0));
    }
}
