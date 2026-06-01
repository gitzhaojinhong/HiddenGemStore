package com.hiddengemstore.config;

import com.hiddengemstore.handler.BloomFilterHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * 布隆过滤器注册类，根据配置，在bean定义阶段注册多个布隆过滤器。
 * <br>
 * 实现 BeanDefinitionRegistryPostProcessor 接口：
 *   这是 Spring Bean 生命周期中的一个扩展点，允许在 Bean 定义阶段（Bean 还没被实例化）修改或新增 Bean 定义。
 *   这里用它是因为需要根据配置动态注册多个 BloomFilterHandler Bean，而不是写死在代码里。
 * <br>
 * 实现 PriorityOrdered 接口：
 *   用于控制多个 BeanDefinitionRegistryPostProcessor 的执行顺序。
 *   返回 HIGHEST_PRECEDENCE 表示最高优先级，确保在其他后处理器之前执行。
 *
 * @author : ZhaoJH
 */
public class BloomFilterHandlerRegistrar implements BeanDefinitionRegistryPostProcessor , PriorityOrdered {
    /**
     * Spring 的环境抽象对象。
     * 包含了所有的配置源（application.yml、系统环境变量、JVM 参数等），
     * 可以用来读取配置值。这里需要用它来解析配置文件中的布隆过滤器配置。
     */
    private final Environment environment;

    public BloomFilterHandlerRegistrar(Environment environment) {
        this.environment = environment;
    }

    /**
     * 核心方法：在 Bean 定义阶段，根据配置动态注册多个 BloomFilterHandler Bean。
     * <br>
     * BeanDefinitionRegistry：Bean 定义注册器，是 Spring 容器中负责管理 Bean 定义的核心接口。
     *   可以通过它手动注册、移除、查询 Bean 定义（即告诉 Spring "我要创建这个 Bean"）。
     * <br>
     * RootBeanDefinition：Bean 定义的具体实现类，包含了创建一个 Bean 所需的全部信息：
     *   - Bean 的类型（这里指 BloomFilterHandler）
     *   - 构造方法参数（这里指 redissonClient、name、expectedInsertions、falseProbability）
     *   - 作用域、初始化方法等其他元数据
     * <br>
     * RuntimeBeanReference：表示一个"运行时 Bean 引用"，告诉 Spring 在创建 Bean 时
     *   需要注入另一个已经存在的 Bean（这里指注入名为 "redissonClient" 的 Bean）。
     *
     * @param registry Bean定义注册器
     */
    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        Map<String, BloomFilterProperties.Filter> filters =  resolveFiltersFromEnvironment();
        filters.forEach((alias, filter) -> {
            String beanName = StringUtils.hasText(filter.getName()) ? filter.getName() : alias;

            RootBeanDefinition bd = new RootBeanDefinition(BloomFilterHandler.class);
            // 构造方法参数,指定一个名为"redissonClient"的bean
            bd.getConstructorArgumentValues().addIndexedArgumentValue(0,new RuntimeBeanReference("redissonClient"));
            bd.getConstructorArgumentValues().addIndexedArgumentValue(1, beanName);
            bd.getConstructorArgumentValues().addIndexedArgumentValue(2, filter.getExpectedInsertions());
            bd.getConstructorArgumentValues().addIndexedArgumentValue(3, filter.getFalseProbability());

            // 注册 Bean
            registry.registerBeanDefinition(beanName, bd);
            // 如果别名和 Bean 名称不一致，则注册别名
            if (!beanName.equals(alias)) {
                registry.registerAlias(beanName, alias);
            }
        });

    }

    /**
     * 从配置文件中解析出布隆过滤器配置。
     * <br>
     * Binder 是 Spring Boot 2.0 引入的类型安全配置绑定工具。
     * 作用：将配置文件（如 application.yml）中的属性值，自动绑定到 Java 对象。
     * 比如将 YAML 中的 "bloom-filter.filters.shop.expected-insertions: 50000"
     * 自动映射到 BloomFilterProperties.Filter 对象的 expectedInsertions 字段。
     * <br>
     * 为什么不用 @ConfigurationProperties 注入？
     *   因为 BloomFilterHandlerRegistrar 是通过 BeanDefinitionRegistryPostProcessor
     *   在 Bean 定义阶段运行的，此时 @ConfigurationProperties 注入还没完成，
     *   所以需要手动使用 Binder 来读取配置。
     *
     * @return 过滤器配置 Map，key 是配置中的别名（如 "shop"），value 是 Filter 配置对象
     */
    private Map<String, BloomFilterProperties.Filter> resolveFiltersFromEnvironment() {
        Binder binder = Binder.get(environment);
        return binder
                .bind("bloom-filter.filters", Bindable.mapOf(String.class, BloomFilterProperties.Filter.class))
                .orElse(Collections.emptyMap());
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinitionRegistryPostProcessor.super.postProcessBeanFactory(beanFactory);
    }

    /**
     * 返回该处理器的执行优先级。
     * <br>
     * HIGHEST_PRECEDENCE（值为 Integer.MIN_VALUE）表示最高优先级。
     * 为什么需要高优先级？
     *   1. 确保布隆过滤器 Bean 在其他后处理器之前注册，这样后续处理器才能正常引用这些 Bean。
     *   2. 如果优先级不够高，其他可能依赖这些 Bean 的处理器会找不到它们。
     *
     * @return HIGHEST_PRECEDENCE 表示最高优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
