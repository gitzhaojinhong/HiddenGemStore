package com.hiddengemstore.redis.internal;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hiddengemstore.redis.api.RedisCache;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link com.hiddengemstore.redis.api.RedisCache} 接口的默认实现，基于 {@link org.springframework.data.redis.core.StringRedisTemplate}。
 * 所有缓存值均以 JSON 字符串形式存储，读取时通过 fastjson 反序列化为目标类型。默认过期时间单位为秒。
 *
 * @author : ZhaoJH
 */
@AllArgsConstructor
public class RedisCacheImpl implements RedisCache {

    private StringRedisTemplate stringRedisTemplate;

    @Override
    public <T> T get(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        // 校验参数非空
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();

        // 从 Redis 获取字符串值
        String cacheValue = stringRedisTemplate.opsForValue().get(key);

        // 如果期望返回 String 类型，直接返回
        if (String.class.isAssignableFrom(clazz)) {
            // 屏蔽报黄，逻辑上clazz是String类型，cacheValue也是String类型
            @SuppressWarnings("unchecked")
            T result = (T) cacheValue;
            return result;
        }
        // 否则处理复杂对象（如 POJO、集合等）
        return getComplex(cacheValue, clazz);
    }

    @Override
    public <T> T get(RedisKeyBuild redisKeyBuild, Class<T> clazz, Supplier<T> supplier, long ttl, TimeUnit timeUnit) {
        T t = get(redisKeyBuild, clazz);
        if (CacheUtil.isEmpty(t)) {
            t = supplier.get();
            if (CacheUtil.isEmpty(t)) {
                return null;
            }
            set(redisKeyBuild, t, ttl, timeUnit);
        }
        return t;
    }

    @Override
    public String getRange(RedisKeyBuild redisKeyBuild, long start, long end) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForValue().get(key, start, end);
    }

    @Override
    public <T> List<T> getValueIsList(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String valueStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isEmpty(valueStr)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(valueStr, clazz);
    }

    @Override
    public <T> List<T> getValueIsList(RedisKeyBuild redisKeyBuild, Class<T> clazz, Supplier<List<T>> supplier, long ttl, TimeUnit timeUnit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String valueStr = stringRedisTemplate.opsForValue().get(key);
        List<T> tList = null;
        if (CacheUtil.isEmpty(valueStr)) {
            tList = supplier.get();
            if (CacheUtil.isEmpty(tList)) {
                return null;
            }
            set(redisKeyBuild, tList, ttl, timeUnit);
        }
        return tList;
    }


    @Override
    public List<String> getKeys(List<RedisKeyBuild> keyList) {
        CacheUtil.checkNotEmpty(keyList);
        List<String> batchKey = CacheUtil.getBatchKey(keyList);
        List<String> list = stringRedisTemplate.opsForValue().multiGet(batchKey);

        return CacheUtil.optimizeRedisList(stringRedisTemplate.opsForValue().multiGet(CacheUtil.optimizeRedisList(list)));
    }

    @Override
    public Boolean hasKey(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public Long getExpire(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.getExpire(key);
    }

    @Override
    public Long getExpire(RedisKeyBuild redisKeyBuild, TimeUnit timeUnit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.getExpire(key, timeUnit);
    }

    @Override
    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    @Override
    public Boolean move(RedisKeyBuild redisKeyBuild, int dbIndex) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.move(key, dbIndex);
    }

    @Override
    public Boolean persist(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.persist(key);
    }

    @Override
    public String randomKey() {
        return stringRedisTemplate.randomKey();
    }

    @Override
    public void rename(RedisKeyBuild oldKey, RedisKeyBuild newKey) {
        CacheUtil.checkNotBlank(oldKey);
        CacheUtil.checkNotBlank(newKey);
        stringRedisTemplate.rename(oldKey.getRelKey(), newKey.getRelKey());
    }

    @Override
    public Boolean renameIfAbsent(RedisKeyBuild oldKey, RedisKeyBuild newKey) {
        CacheUtil.checkNotBlank(oldKey);
        CacheUtil.checkNotBlank(newKey);
        return stringRedisTemplate.renameIfAbsent(oldKey.getRelKey(), newKey.getRelKey());
    }

    @Override
    public DataType type(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.type(key);
    }

    @Override
    public void set(RedisKeyBuild redisKeyBuild, Object object) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String json = object instanceof String ? (String) object : JSON.toJSONString(object);
        stringRedisTemplate.opsForValue().set(key, json);
    }

    @Override
    public void set(RedisKeyBuild redisKeyBuild, Object object, long ttl) {
        set(redisKeyBuild, object, ttl, CacheUtil.DEFAULT_TIME_UNIT);
    }

    @Override
    public void set(RedisKeyBuild redisKeyBuild, Object object, long ttl, TimeUnit timeUnit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String json = object instanceof String ? (String) object : JSON.toJSONString(object);
        stringRedisTemplate.opsForValue().set(key, json, ttl, timeUnit);
    }

    @Override
    public boolean setIfAbsent(RedisKeyBuild redisKeyBuild, Object object) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String json = object instanceof String ? (String) object : JSON.toJSONString(object);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, json));
    }

    @Override
    public boolean setIfAbsent(RedisKeyBuild redisKeyBuild, Object object, long ttl, TimeUnit timeUnit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String json = object instanceof String ? (String) object : JSON.toJSONString(object);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, json, ttl, timeUnit));
    }

    @Override
    public Long size(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForValue().size(key);
    }

    @Override
    public void multiSet(Map<RedisKeyBuild, ?> map) {
        CacheUtil.checkNotEmpty(map);
        Map<String, String> mapForSave = new HashMap<>(map.size());
        map.forEach((hashKey, val) -> {
            String jsonValue = val instanceof String ? (String) val : JSON.toJSONString(val);
            mapForSave.put(hashKey.getRelKey(), jsonValue);
        });
        stringRedisTemplate.opsForValue().multiSet(mapForSave);
    }

    @Override
    public boolean multiSetIfAbsent(Map<RedisKeyBuild, ?> map) {
        CacheUtil.checkNotEmpty(map);
        Map<String, String> mapForSave = new HashMap<>(map.size());
        map.forEach((hashKey, val) -> {
            String jsonValue = val instanceof String ? (String) val : JSON.toJSONString(val);
            mapForSave.put(hashKey.getRelKey(), jsonValue);
        });
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().multiSetIfAbsent(mapForSave));
    }

    @Override
    public Long incrBy(RedisKeyBuild redisKeyBuild, long increment) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForValue().increment(key, increment);
    }

    @Override
    public Double incrByDouble(RedisKeyBuild redisKeyBuild, double increment) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForValue().increment(key, increment);
    }

    @Override
    public Integer append(RedisKeyBuild redisKeyBuild, String value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForValue().append(key, value);
    }

    @Override
    public void putHash(RedisKeyBuild redisKeyBuild, String hashKey, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        stringRedisTemplate.opsForHash().put(key, hashKey, jsonValue);
    }

    @Override
    public void putHash(RedisKeyBuild redisKeyBuild, String hashKey, Object value, long ttl) {
        putHash(redisKeyBuild, hashKey, value, ttl, CacheUtil.DEFAULT_TIME_UNIT);
    }

    @Override
    public void putHash(RedisKeyBuild redisKeyBuild, String hashKey, Object value, long ttl, TimeUnit timeUnit) {
        putHash(redisKeyBuild, hashKey, value);
        // 设置过期时间
        expire(redisKeyBuild, ttl, timeUnit);
    }

    @Override
    public void putHash(RedisKeyBuild redisKeyBuild, Map<String, ?> map) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Map<String, String> mapForSave = new HashMap<>(map.size());
        map.forEach((hashKey, val) -> {
            String jsonValue = val instanceof String ? (String) val : JSON.toJSONString(val);
            mapForSave.put(hashKey, jsonValue);
        });
        stringRedisTemplate.opsForHash().putAll(key, mapForSave);
    }

    @Override
    public void putHash(RedisKeyBuild redisKeyBuild, Map<String, ?> map, long ttl) {
        putHash(redisKeyBuild, map, ttl, CacheUtil.DEFAULT_TIME_UNIT);
    }

    @Override
    public void putHash(RedisKeyBuild redisKeyBuild, Map<String, ?> map, long ttl, TimeUnit timeUnit) {
        putHash(redisKeyBuild, map);
        expire(redisKeyBuild, ttl, timeUnit);
    }

    @Override
    public Boolean putHashIfAbsent(RedisKeyBuild redisKeyBuild, String hashKey, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForHash().putIfAbsent(key, hashKey, jsonValue);
    }

    @Override
    public <T> T getForHash(RedisKeyBuild redisKeyBuild, String hashKey, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        Object o = stringRedisTemplate.opsForHash().get(key, hashKey);
        // 如果取String类型 则直接取出返回
        if (String.class.isAssignableFrom(clazz)) {
            // 此处 clazz 为 String 类型，o 也是 String，强转 T 安全，但编译器无法验证泛型类型
            @SuppressWarnings("unchecked")
            T result = (T) o;
            return result;
        }
        return getComplex(o, clazz);
    }

    @Override
    public <T> List<T> getValueIsListForHash(RedisKeyBuild redisKeyBuild, String hashKey, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        Object o = stringRedisTemplate.opsForHash().get(key, hashKey);
        if (o == null) {
            return new ArrayList<>();
        }
        List<T> list = new ArrayList<>();
        if (o instanceof String) {
            list = JSON.parseArray((String) o, clazz);
        }
        return list;
    }

    @Override
    public <T> List<T> multiGetForHash(RedisKeyBuild redisKeyBuild, List<String> hashKeys, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKeys);
        String key = redisKeyBuild.getRelKey();
        List<Object> objHashKeys = new ArrayList<>(hashKeys);
        List<Object> multiGetObj = stringRedisTemplate.opsForHash().multiGet(key, objHashKeys);

        if (CacheUtil.checkRedisListIsEmpty(multiGetObj)) {
            return new ArrayList<>();
        }
        if (String.class.isAssignableFrom(clazz)) {
            // 此处 clazz 为 String 类型，multiGetObj 实际为 List<String>，强转 List<T> 安全
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) multiGetObj;
            return result;
        }

        return parseObjects(multiGetObj, clazz);
    }

    @Override
    public <T> List<T> getAllForHash(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<Object> valuesObj = stringRedisTemplate.opsForHash().values(key);
        if (CacheUtil.checkRedisListIsEmpty(valuesObj)) {
            return new ArrayList<>();
        }
        if (String.class.isAssignableFrom(clazz)) {
            // 此处 clazz 为 String 类型，valuesObj 实际为 List<String>，强转 List<T> 安全
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) valuesObj;
            return result;
        }

        return parseObjects(valuesObj, clazz);
    }

    @Override
    public <T> Map<String, T> getAllMapForHash(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, T> map = new HashMap<>(64);
        entries.forEach((k, v) -> map.put(String.valueOf(k), getComplex(v, clazz)));
        return map;
    }

    @Override
    public <T> T indexForList(RedisKeyBuild redisKeyBuild, long index, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String cachedValue = stringRedisTemplate.opsForList().index(key, index);
        if (StrUtil.isEmpty(cachedValue)) {
            return null;
        }
        if (String.class.isAssignableFrom(clazz)) {
            // 此处 clazz 为 String 类型，cachedValue 也是 String，强转 T 安全
            @SuppressWarnings("unchecked")
            T result = (T) cachedValue;
            return result;
        }
        return getComplex(cachedValue, clazz);
    }

    @Override
    public Long leftPushForList(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().leftPush(key, jsonValue);
    }

    @Override
    public Long leftPushAllForList(RedisKeyBuild redisKeyBuild, List<?> valueList) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(valueList);
        String key = redisKeyBuild.getRelKey();
        List<String> jsonList = new ArrayList<>(valueList.size());
        valueList.forEach(value -> {
            String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
            jsonList.add(jsonValue);
        });
        return stringRedisTemplate.opsForList().leftPushAll(key, jsonList);
    }

    @Override
    public Long leftPushIfPresentForList(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().leftPushIfPresent(key, jsonValue);
    }

    @Override
    public Long leftPushForList(RedisKeyBuild redisKeyBuild, Object pivot, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(pivot);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonPivot = value instanceof String ? (String) pivot : JSON.toJSONString(pivot);
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().leftPush(key, jsonPivot, jsonValue);
    }

    @Override
    public Long rightPushForList(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().rightPush(key, jsonValue);
    }

    @Override
    public Long rightPushAllForList(RedisKeyBuild redisKeyBuild, List<Object> valueList) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(valueList);
        String key = redisKeyBuild.getRelKey();
        List<String> jsonList = new ArrayList<>(valueList.size());
        valueList.forEach(value -> {
            String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
            jsonList.add(jsonValue);
        });
        return stringRedisTemplate.opsForList().rightPushAll(key, jsonList);
    }

    @Override
    public Long rightPushIfPresentForList(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().rightPushIfPresent(key, jsonValue);
    }

    @Override
    public Long rightPushForList(RedisKeyBuild redisKeyBuild, Object pivot, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(pivot);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonPivot = value instanceof String ? (String) pivot : JSON.toJSONString(pivot);
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().rightPush(key, jsonPivot, jsonValue);
    }

    @Override
    public void setForList(RedisKeyBuild redisKeyBuild, long index, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        stringRedisTemplate.opsForList().set(key, index, jsonValue);
    }

    @Override
    public <T> T leftPopForList(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String s = stringRedisTemplate.opsForList().leftPop(key);
        return getComplex(s, clazz);
    }

    @Override
    public <T> T leftPopBlockForList(RedisKeyBuild redisKeyBuild, Class<T> clazz, long timeout, TimeUnit unit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String s = stringRedisTemplate.opsForList().leftPop(key, timeout, unit);
        return getComplex(s, clazz);
    }

    @Override
    public <T> T rightPopForList(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String s = stringRedisTemplate.opsForList().rightPop(key);
        return getComplex(s, clazz);
    }

    @Override
    public <T> T rightPopBlockForList(RedisKeyBuild redisKeyBuild, Class<T> clazz, long timeout, TimeUnit unit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String s = stringRedisTemplate.opsForList().rightPop(key, timeout, unit);
        return getComplex(s, clazz);
    }

    @Override
    public <T> T rightPopAndLeftPushForList(RedisKeyBuild sourceKey, RedisKeyBuild destinationKey, Class<T> clazz) {
        CacheUtil.checkNotBlank(sourceKey);
        CacheUtil.checkNotBlank(destinationKey);
        String sourceRelKey = sourceKey.getRelKey();
        String destinationRelKey = destinationKey.getRelKey();
        String s = stringRedisTemplate.opsForList().rightPopAndLeftPush(sourceRelKey, destinationRelKey);
        return getComplex(s, clazz);
    }

    @Override
    public <T> T rightPopBlockAndLeftPushForList(RedisKeyBuild sourceKey, RedisKeyBuild destinationKey, Class<T> clazz, long timeout, TimeUnit unit) {
        CacheUtil.checkNotBlank(sourceKey);
        CacheUtil.checkNotBlank(destinationKey);
        String sourceRelKey = sourceKey.getRelKey();
        String destinationRelKey = destinationKey.getRelKey();
        String s = stringRedisTemplate.opsForList().rightPopAndLeftPush(sourceRelKey, destinationRelKey, timeout, unit);
        return getComplex(s, clazz);
    }

    @Override
    public <T> List<T> getAllForList(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (CacheUtil.checkRedisListIsEmpty(list)) {
            return new ArrayList<>();
        }
        return parseObjects(list, clazz);
    }

    @Override
    public <T> List<T> rangeForList(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> range = stringRedisTemplate.opsForList().range(key, start, end);
        if (CacheUtil.checkRedisListIsEmpty(range)) {
            return new ArrayList<>();
        }
        return parseObjects(range, clazz);
    }

    @Override
    public Long removeForList(RedisKeyBuild redisKeyBuild, long index, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForList().remove(key, index, jsonValue);
    }

    @Override
    public void trimForList(RedisKeyBuild redisKeyBuild, long start, long end) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        stringRedisTemplate.opsForList().trim(key, start, end);
    }

    @Override
    public Long lenForList(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForList().size(key);
    }

    @Override
    public Boolean hasKeyForHash(RedisKeyBuild redisKeyBuild, String hashKey) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForHash().hasKey(key, hashKey);
    }

    @Override
    public void del(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        stringRedisTemplate.delete(key);
    }


    @Override
    public Long delForHash(RedisKeyBuild redisKeyBuild, String hashKey) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForHash().delete(key, hashKey);
    }

    @Override
    public Long delForHash(RedisKeyBuild redisKeyBuild, Collection<String> hashKeys) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKeys);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForHash().delete(key, hashKeys.toArray());
    }

    @Override
    public Long incrByForHash(RedisKeyBuild redisKeyBuild, String hashKey, long increment) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForHash().increment(key, hashKey, increment);
    }

    @Override
    public Double incrByDoubleForHash(RedisKeyBuild redisKeyBuild, String hashKey, double delta) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(hashKey);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    @Override
    public Set<String> hashKeysForHash(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<Object> keys = stringRedisTemplate.opsForHash().keys(key);
        return parseObjects(keys, String.class);
    }

    @Override
    public Long sizeForHash(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForHash().size(key);
    }

    @Override
    public void del(Collection<RedisKeyBuild> keys) {
        CacheUtil.checkNotEmpty(keys);
        List<String> batchKey = CacheUtil.getBatchKey(keys);
        stringRedisTemplate.delete(batchKey);
    }

    @Override
    public void expire(RedisKeyBuild redisKeyBuild, long ttl, TimeUnit timeUnit) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        stringRedisTemplate.expire(key, ttl, timeUnit);
    }

    @Override
    public Long addForSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForSet().add(key, jsonValue);
    }

    @Override
    public Long addForSet(RedisKeyBuild redisKeyBuild, List<?> values) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(values);
        String key = redisKeyBuild.getRelKey();
        List<String> jsonList = new ArrayList<>(values.size());
        values.forEach(value -> {
            String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
            jsonList.add(jsonValue);
        });
        return stringRedisTemplate.opsForSet().add(key, jsonList.toArray(new String[]{}));
    }

    @Override
    public Long removeForSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForSet().remove(key, jsonValue);
    }

    @Override
    public Long removeForSet(RedisKeyBuild redisKeyBuild, List<?> values) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(values);
        String key = redisKeyBuild.getRelKey();
        List<String> jsonList = values.stream()
                .map(value -> value instanceof String ? (String) value : JSON.toJSONString(value))
                .toList();
        return stringRedisTemplate.opsForSet().remove(key, (Object) jsonList.toArray(new String[]{}));
    }

    @Override
    public <T> T popForSet(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String cachedValue = stringRedisTemplate.opsForSet().pop(key);
        return getComplex(cachedValue, clazz);
    }

    @Override
    public boolean moveForSet(RedisKeyBuild redisKeyBuild, Object value, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String destKey = destRedisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().move(key, jsonValue, destKey));
    }

    @Override
    public Long sizeForSet(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().size(key);
    }

    @Override
    public Boolean isMemberForSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForSet().isMember(key, jsonValue);
    }

    @Override
    public <T> Set<T> intersectForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForSet().intersect(key, otherKey);
        return parseObjects(set, clazz);
    }

    @Override
    public <T> Set<T> intersectForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        Set<String> set = stringRedisTemplate.opsForSet().intersect(key, otherKeys);
        return parseObjects(set, clazz);
    }

    @Override
    public Long intersectAndStoreForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().intersectAndStore(key, otherKey, destKey);
    }

    @Override
    public Long intersectAndStoreForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().intersectAndStore(key, otherKeys, destKey);
    }

    @Override
    public <T> Set<T> unionForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForSet().union(key, otherKey);
        return parseObjects(set, clazz);
    }

    @Override
    public <T> Set<T> unionForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        Set<String> set = stringRedisTemplate.opsForSet().union(key, otherKeys);
        return parseObjects(set, clazz);
    }

    @Override
    public Long unionAndStoreForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().unionAndStore(key, otherKey, destKey);
    }

    @Override
    public Long unionAndStoreForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().unionAndStore(key, otherKeys, destKey);
    }

    @Override
    public <T> Set<T> differenceForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForSet().difference(key, otherKey);
        return parseObjects(set, clazz);
    }

    @Override
    public <T> Set<T> differenceForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        Set<String> set = stringRedisTemplate.opsForSet().difference(key, otherKeys);
        return parseObjects(set, clazz);
    }

    @Override
    public Long differenceForSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().differenceAndStore(key, otherKey, destKey);
    }

    @Override
    public Long differenceForSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().differenceAndStore(key, otherKeys, destKey);
    }

    @Override
    public <T> Set<T> membersForSet(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        return parseObjects(members, clazz);
    }

    @Override
    public <T> T randomMemberForSet(RedisKeyBuild redisKeyBuild, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String s = stringRedisTemplate.opsForSet().randomMember(key);
        return getComplex(s, clazz);
    }

    @Override
    public <T> List<T> randomMembersForSet(RedisKeyBuild redisKeyBuild, long count, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> list = stringRedisTemplate.opsForSet().randomMembers(key, count);
        if (CacheUtil.checkRedisListIsEmpty(list)) {
            return new ArrayList<>();
        }
        return parseObjects(list, clazz);
    }

    @Override
    public <T> Set<T> distinctRandomMembersForSet(RedisKeyBuild redisKeyBuild, long count, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForSet().distinctRandomMembers(key, count);
        return parseObjects(set, clazz);
    }

    @Override
    public Cursor<String> scanForSet(RedisKeyBuild redisKeyBuild, ScanOptions options) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForSet().scan(key, options);
    }

    @Override
    public void addForSortedSet(RedisKeyBuild redisKeyBuild, Object value, Double score) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        CacheUtil.checkNotEmpty(score);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        stringRedisTemplate.opsForZSet().add(key, jsonValue, score);
    }

    @Override
    public void addForSortedSet(RedisKeyBuild redisKeyBuild, Object value, Double score, long ttl) {
        addForSortedSet(redisKeyBuild, value, score, ttl, CacheUtil.DEFAULT_TIME_UNIT);
    }

    @Override
    public void addForSortedSet(RedisKeyBuild redisKeyBuild, Object value, Double score, long ttl, TimeUnit timeUnit) {
        addForSortedSet(redisKeyBuild, value, score);
        expire(redisKeyBuild, ttl, timeUnit);
    }

    @Override
    public Long addForSortedSet(RedisKeyBuild redisKeyBuild, Map<?, Double> map) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<ZSetOperations.TypedTuple<String>> collect =
                map.entrySet()
                        .stream()
                        .map(item -> {
                            String entryKey = item.getKey() instanceof String ? (String) item.getKey() : JSON.toJSONString(item.getKey());
                            return new DefaultTypedTuple<>(entryKey, item.getValue());
                        })
                        .collect(Collectors.toSet());
        return stringRedisTemplate.opsForZSet().add(key, collect);
    }

    @Override
    public Long addForSortedSet(RedisKeyBuild redisKeyBuild, Map<?, Double> map, long ttl) {
        return addForSortedSet(redisKeyBuild, map, ttl, CacheUtil.DEFAULT_TIME_UNIT);
    }

    @Override
    public Long addForSortedSet(RedisKeyBuild redisKeyBuild, Map<?, Double> map, long ttl, TimeUnit timeUnit) {
        Long count = addForSortedSet(redisKeyBuild, map);
        expire(redisKeyBuild, ttl, timeUnit);
        return count;
    }

    @Override
    public <T> Set<T> getRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> resultSet = stringRedisTemplate.opsForZSet().range(key, start, end);
        return parseObjects(resultSet, clazz);
    }

    @Override
    public <T> Set<T> getReverseRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> resultSet = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return parseObjects(resultSet, clazz);
    }

    @Override
    public Long delForSortedSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForZSet().remove(key, jsonValue);
    }

    @Override
    public Long delForSortedSet(RedisKeyBuild redisKeyBuild, Collection<?> valueCollection) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(valueCollection);
        String key = redisKeyBuild.getRelKey();
        List<String> jsonValueList = valueCollection.stream()
                .map(value -> value instanceof String ? (String) value : JSON.toJSONString(value))
                .distinct()
                .toList();
        return stringRedisTemplate.opsForZSet().remove(key, jsonValueList.toArray());
    }

    @Override
    public Long delRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().removeRange(key, start, end);
    }

    @Override
    public Double incrementScoreForSortedSet(RedisKeyBuild redisKeyBuild, Object value, double delta) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForZSet().incrementScore(key, jsonValue, delta);
    }

    @Override
    public Long sizeForSortedSet(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().size(key);
    }

    @Override
    public Long rankForSortedSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForZSet().rank(key, jsonValue);
    }

    @Override
    public Long reverseRankForSortedSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForZSet().reverseRank(key, jsonValue);
    }

    @Override
    public <T> Set<ZSetOperations.TypedTuple<T>> rangeWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<ZSetOperations.TypedTuple<String>> cacheSet = stringRedisTemplate.opsForZSet().rangeWithScores(key, start, end);
        if (cacheSet == null) {
            return new HashSet<>();
        }
        return typedTupleStringParseObjects(cacheSet, clazz);
    }

    @Override
    public <T> Set<T> rangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForZSet().rangeByScore(key, min, max);
        return parseObjects(set, clazz);
    }

    @Override
    public <T> Set<ZSetOperations.TypedTuple<T>> rangeByScoreWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<ZSetOperations.TypedTuple<String>> cacheSet = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(key, min, max);
        return typedTupleStringParseObjects(cacheSet, clazz);
    }

    @Override
    public <T> Set<ZSetOperations.TypedTuple<T>> rangeByScoreWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max,
                                                                                   long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<ZSetOperations.TypedTuple<String>> cacheSet = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(key, min, max, start, end);
        return typedTupleStringParseObjects(cacheSet, clazz);
    }

    @Override
    public <T> Set<ZSetOperations.TypedTuple<T>> reverseRangeWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<ZSetOperations.TypedTuple<String>> cacheSet = stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        return typedTupleStringParseObjects(cacheSet, clazz);
    }

    @Override
    public <T> Set<T> reverseRangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRangeByScore(key, min, max);
        return parseObjects(set, clazz);
    }

    @Override
    public <T> Set<ZSetOperations.TypedTuple<T>> reverseRangeByScoreWithScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<ZSetOperations.TypedTuple<String>> cacheSet = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, min, max);
        return typedTupleStringParseObjects(cacheSet, clazz);
    }

    @Override
    public <T> Set<T> reverseRangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max, long start, long end, Class<T> clazz) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRangeByScore(key, min, max, start, end);
        return parseObjects(set, clazz);
    }

    @Override
    public Long countForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().count(key, min, max);
    }

    @Override
    public Long zCardForSortedSet(RedisKeyBuild redisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().zCard(key);
    }

    @Override
    public Double scoreByValueForSortedSet(RedisKeyBuild redisKeyBuild, Object value) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(value);
        String key = redisKeyBuild.getRelKey();
        String jsonValue = value instanceof String ? (String) value : JSON.toJSONString(value);
        return stringRedisTemplate.opsForZSet().score(key, jsonValue);
    }

    @Override
    public Long removeRangeForSortedSet(RedisKeyBuild redisKeyBuild, long start, long end) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().removeRange(key, start, end);
    }

    @Override
    public Long removeRangeByScoreForSortedSet(RedisKeyBuild redisKeyBuild, double min, double max) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    @Override
    public Long unionAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().unionAndStore(key, otherKey, destKey);
    }

    @Override
    public Long unionAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().unionAndStore(key, otherKeys, destKey);
    }

    @Override
    public Long intersectAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, RedisKeyBuild otherRedisKeyBuild, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotBlank(otherRedisKeyBuild);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        String otherKey = otherRedisKeyBuild.getRelKey();
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().intersectAndStore(key, otherKey, destKey);
    }

    @Override
    public Long intersectAndStoreForSortedSet(RedisKeyBuild redisKeyBuild, Collection<RedisKeyBuild> otherRedisKeyBuilds, RedisKeyBuild destRedisKeyBuild) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        CacheUtil.checkNotEmpty(otherRedisKeyBuilds);
        CacheUtil.checkNotBlank(destRedisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        List<String> otherKeys = CacheUtil.getBatchKey(otherRedisKeyBuilds);
        String destKey = destRedisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().intersectAndStore(key, otherKeys, destKey);
    }

    @Override
    public Cursor<ZSetOperations.TypedTuple<String>> scanForSortedSet(RedisKeyBuild redisKeyBuild, ScanOptions options) {
        CacheUtil.checkNotBlank(redisKeyBuild);
        String key = redisKeyBuild.getRelKey();
        return stringRedisTemplate.opsForZSet().scan(key, options);
    }

    @Override
    public <T> T getByType(RedisKeyBuild redisKeyBuild, Type genericReturnType) {
        String key = redisKeyBuild.getRelKey();
        String s = stringRedisTemplate.boundValueOps(key).get();
        if (StrUtil.isEmpty(s)) {
            return null;
        }
        return JSONObject.parseObject(s, genericReturnType);
    }

    @Override
    public RedisTemplate<String,String> getInstance() {
        return stringRedisTemplate;
    }


    /**
     * 将源数据反序列化为目标类型的对象。若目标类型为 String，直接返回或转换为 JSON 字符串；
     * 否则将字符串源数据通过 fastjson 反序列化为目标类型。
     *
     * @param source 源数据，通常为 String 或 Object
     * @param clazz  目标类型的 Class 对象
     * @param <T>    目标类型泛型
     * @return 反序列化后的对象，源数据为 {@code null} 或无法转换时返回 {@code null}
     */
    private <T> T getComplex(Object source, Class<T> clazz) {
        if (source == null) {
            return null;
        }

        // 如果期望返回 String 类型 或 是其父类
        if (clazz.isAssignableFrom(String.class)) {
            // 源数据已是 String，直接返回
            if (source instanceof String) {
                // 此处 clazz 为 String 类型，source 也是 String，强转 T 安全
                @SuppressWarnings("unchecked")
                T result = (T) source;
                return result;
            } else {
                // 源数据是对象，转为 JSON 字符串；clazz 为 String 类型，toJSONString 返回 String，强转 T 安全
                @SuppressWarnings("unchecked")
                T result = (T) JSON.toJSONString(source);
                return result;
            }
        }

        // 源数据是字符串时，反序列化为目标类型；否则返回 null
        return source instanceof String ? JSON.parseObject((String) source, CacheUtil.buildType(clazz)) : null;
    }

    /**
     * 将 Object 列表批量反序列化为指定类型的 List。若目标类型为 String，直接转换；
     * 否则将每个字符串元素通过 fastjson 反序列化。
     *
     * @param sources 源数据列表
     * @param clazz   目标元素类型的 Class 对象
     * @param <T>     目标元素类型泛型
     * @return 反序列化后的 List，源数据为 {@code null} 时返回空集合
     */
    public <T> List<T> parseObjects(List<?> sources, Class<T> clazz) {
        if (sources == null) {
            return new ArrayList<>();
        }
        if (clazz.isAssignableFrom(String.class)) {
            // clazz 为 String 类型，stream map 后实际为 List<String>，强转 List<T> 安全
            @SuppressWarnings("unchecked")
            List<T> resultList = (List<T>) sources.stream()
                    .map(each -> each instanceof String ? (String) each : JSON.toJSONString(each))
                    .collect(Collectors.toList());
            return resultList;
        }
        // stream map 后实际为 List<Object>，强转为 List<T> 依赖 clazz 的正确性，由调用方保证
        @SuppressWarnings("unchecked")
        List<T> resultList = (List<T>) sources.stream()
                .map(each -> each instanceof String ? JSON.parseObject((String) each, CacheUtil.buildType(clazz)) : null)
                .collect(Collectors.toList());
        return resultList;
    }

    /**
     * 将 Object 集合批量反序列化为指定类型的 Set。若目标类型为 String，直接转换；
     * 否则将每个字符串元素通过 fastjson 反序列化。
     *
     * @param sources 源数据集合
     * @param clazz   目标元素类型的 Class 对象
     * @param <T>     目标元素类型泛型
     * @return 反序列化后的 Set，源数据为 {@code null} 时返回空集合
     */
    public <T> Set<T> parseObjects(Set<?> sources, Class<T> clazz) {
        if (sources == null) {
            return new HashSet<>();
        }
        if (clazz.isAssignableFrom(String.class)) {
            // clazz 为 String 类型，stream map 后实际为 Set<String>，强转 Set<T> 安全
            @SuppressWarnings("unchecked")
            Set<T> resultSet = (Set<T>) sources.stream()
                    .map(each -> each instanceof String ? (String) each : JSON.toJSONString(each))
                    .collect(Collectors.toSet());
            return resultSet;
        }
        // stream map 后实际为 Set<Object>，强转为 Set<T> 依赖 clazz 的正确性，由调用方保证
        @SuppressWarnings("unchecked")
        Set<T> resultSet = (Set<T>) sources.stream()
                .map(each -> each instanceof String ? JSON.parseObject((String) each, CacheUtil.buildType(clazz)) : null)
                .collect(Collectors.toSet());
        return resultSet;
    }

    /**
     * 将 String 类型的 TypedTuple 集合转换为目标类型的 TypedTuple 集合，保留原始 score。
     *
     * @param sources String 类型的 TypedTuple 集合
     * @param clazz   目标值类型的 Class 对象
     * @param <T>     目标值类型泛型
     * @return 转换后的 TypedTuple 集合，源数据为 {@code null} 时返回空集合
     */
    public <T> Set<ZSetOperations.TypedTuple<T>> typedTupleStringParseObjects(Set<ZSetOperations.TypedTuple<String>> sources, Class<T> clazz) {
        if (sources == null) {
            return new HashSet<>();
        }
        Set<ZSetOperations.TypedTuple<T>> set = new HashSet<>(sources.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : sources) {
            String value = typedTuple.getValue();
            T complex = getComplex(value, clazz);
            Double score = typedTuple.getScore();
            DefaultTypedTuple<T> defaultTypedTuple = new DefaultTypedTuple<>(complex, score);
            set.add(defaultTypedTuple);
        }
        return set;
    }

    /**
     * 将 Object 类型的 TypedTuple 集合转换为目标类型的 TypedTuple 集合，保留原始 score。
     *
     * @param sources Object 类型的 TypedTuple 集合
     * @param clazz   目标值类型的 Class 对象
     * @param <T>     目标值类型泛型
     * @return 转换后的 TypedTuple 集合，源数据为 {@code null} 时返回空集合
     */
    public <T> Set<ZSetOperations.TypedTuple<T>> typedTupleParseObjects(Set<ZSetOperations.TypedTuple<T>> sources, Class<T> clazz) {
        if (sources == null) {
            return new HashSet<>();
        }
        Set<ZSetOperations.TypedTuple<T>> set = new HashSet<>(sources.size());
        for (ZSetOperations.TypedTuple<T> typedTuple : sources) {
            Object value = typedTuple.getValue();
            T complex = getComplex(value, clazz);
            Double score = typedTuple.getScore();
            DefaultTypedTuple<T> defaultTypedTuple = new DefaultTypedTuple<>(complex, score);
            set.add(defaultTypedTuple);
        }
        return set;
    }
}
