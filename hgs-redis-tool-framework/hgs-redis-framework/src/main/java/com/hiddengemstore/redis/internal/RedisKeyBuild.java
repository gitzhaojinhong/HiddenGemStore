package com.hiddengemstore.redis.internal;

import com.hiddengemstore.context.SpringUtil;
import com.hiddengemstore.enums.RedisKeyManage;
import lombok.Getter;

import java.util.Objects;

/**
 * Redis Key 构建器，通过枚举 {@link RedisKeyManage} 和占位符参数生成唯一的 Redis key。
 * <p>生成的 key 格式为：{@code 应用前缀-枚举key格式化后的值}，应用前缀通过 {@link SpringUtil#getPrefixDistinctionName()} 获取，
 * 用于多环境下 key 的隔离。该类为不可变对象，线程安全。</p>
 * @author : ZhaoJH
 */
@Getter
public final class RedisKeyBuild {
    /**
     * 实际使用的key
     **/
    private final String relKey;

    private RedisKeyBuild(String relKey) {
        this.relKey = relKey;
    }

    /**
     * 构建真实的key
     * @param redisKeyManage key的枚举
     * @param args 占位符的值
     **/
    public static RedisKeyBuild createRedisKey(RedisKeyManage redisKeyManage, Object... args){
        String redisRelKey = String.format(redisKeyManage.getKey(),args);
        return new RedisKeyBuild(SpringUtil.getPrefixDistinctionName() + "-" + redisRelKey);
    }

    /**
     * 获取不带占位符参数的 Redis key，适用于 key 模板本身不含格式化占位符的场景
     * @param redisKeyManage key 的枚举
     * @return 拼接应用前缀后的完整 Redis key 字符串
     */
    public static String getRedisKey(RedisKeyManage redisKeyManage) {
        return SpringUtil.getPrefixDistinctionName() + "-" + redisKeyManage.getKey();
    }

    /** 基于 relKey 判断两个 RedisKeyBuild 是否相等 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RedisKeyBuild that = (RedisKeyBuild) o;
        return relKey.equals(that.relKey);
    }

    /** 基于 relKey 计算哈希值 */
    @Override
    public int hashCode() {
        return Objects.hash(relKey);
    }
}
