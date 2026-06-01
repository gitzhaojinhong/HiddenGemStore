package com.hiddengemstore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 布隆过滤器属性配置
 * 示例：
 * bloom-filter:
 *   filters:
 *     shop:                          # ← 这个 key 是别名 alias
 *       name: shop-id-bloom          # ← 这才是真正的 Bean 名称
 *       expected-insertions: 50000
 *       false-probability: 0.01
 *     voucher:                       # ← 这个 key 是别名 alias
 *       name: voucher-id-bloom       # ← 这才是真正的 Bean 名称
 *       expected-insertions: 100000
 *       false-probability: 0.01
 * @author : ZhaoJH
 */
@Data
@ConfigurationProperties(prefix = BloomFilterProperties.PREFIX)
public class BloomFilterProperties {
    public static final String PREFIX = "bloom-filter";

    /**
     * 布隆过滤器配置 Map。
     * key 是配置中的别名（如 "shop"、"voucher"），value 是 Filter 配置对象。
     * <br>
     * 由 Spring Boot 自动填充：通过 @ConfigurationProperties 注解，
     * Spring Boot 会自动将 YAML 配置中 "bloom-filter.filters" 下的 map 结构
     * 绑定到这个字段。比如 YAML 中写了 filters 下有 shop 和 voucher 两个 key，
     * 这里就会得到一个包含两个 Filter 对象的 Map。
     * <br>
     * <b style="color:yellow;">注意：该字段在当前设计中是冗余的。</b>
     * <br>
     * 因为 BloomFilterHandlerRegistrar 实现了 BeanDefinitionRegistryPostProcessor，
     * 运行在 Bean 定义阶段（此时 @ConfigurationProperties 注入还没完成），
     * 所以它使用 Binder 直接从 Environment 读取配置，不依赖此字段。
     * YAML 配置结构不受影响，即使删除此字段，配置仍可正常读取。
     * 保留此字段的原因：
     *   1. 为将来可能的扩展保留可能性（如其他地方需要注入 BloomFilterProperties）
     *   2. 保持配置类结构清晰，作为配置的"声明"
     *   3. 生成 IDE 配置元数据（配置提示、自动补全）
     */
    private Map<String,Filter> filters;

    @Data
    public static class Filter{
        // 过滤器名称
        private String name;
        // 预计插入数量
        private Long expectedInsertions = 20000L;
        // 误判率
        private Double falseProbability = 0.01D;
    }

}
