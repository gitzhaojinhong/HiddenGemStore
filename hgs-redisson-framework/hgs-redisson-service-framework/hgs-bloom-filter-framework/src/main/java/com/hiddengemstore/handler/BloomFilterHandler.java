package com.hiddengemstore.handler;

import com.hiddengemstore.context.SpringUtil;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

/**
 * 单个布隆过滤器处理类，封装了 Redisson 的 RBloomFilter。
 * <br>
 * Redis key 命名规则：{prefix.distinction.name}-{filterName}
 * 例如：prefix="hgs", name="shop-id-bloom" → Redis key: "hgs-shop-id-bloom"
 * <br>
 * tryInit() 是幂等的：如果布隆过滤器已存在且参数相同，不会重复初始化；
 * 如果参数不同，会抛出异常（需要先删除 Redis 中的旧数据）。
 * @author : ZhaoJH
 */
public class BloomFilterHandler {
    /** 布隆过滤器实例 */
    private final RBloomFilter<String> bloomFilter;
    /**
     * 构造方法，初始化布隆过滤器
     * @param redissonClient Redisson客户端
     * @param name 布隆过滤器名称
     * @param expectedInsertions 预期插入元素数量，默认为20000
     * @param falseProbability 误判率，默认为0.01
     */
    public BloomFilterHandler(RedissonClient redissonClient,
                              String name,
                              Long expectedInsertions,
                              Double falseProbability) {
        // Redis key = 应用前缀 + "-" + 过滤器名称，确保多环境/多实例隔离
        RBloomFilter<String> bf = redissonClient.getBloomFilter(SpringUtil.getPrefixDistinctionName()
                + "-"
                + name);
        // 幂等初始化：已存在且参数相同时不重复初始化；参数不同时抛异常
        bf.tryInit(expectedInsertions == null ? 20000L : expectedInsertions,
                falseProbability == null ? 0.01D : falseProbability);

        this.bloomFilter = bf;
    }
    /**
     * 向布隆过滤器中添加元素
     * @param value 要添加的元素值
     * @return 是否添加成功
     */
    public boolean add(String value) {
        return bloomFilter.add(value);
    }
    /**
     * 判断元素是否可能存在于布隆过滤器中
     * @param value 要检查的元素值
     * @return 如果返回false则元素一定不存在，如果返回true则元素可能存在
     */
    public boolean contains(String value) {
        return bloomFilter.contains(value);
    }
}
