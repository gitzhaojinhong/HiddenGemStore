package com.hiddengemstore.context;

import lombok.NonNull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import static com.hiddengemstore.constant.Constant.DEFAULT_PREFIX_DISTINCTION_NAME;
import static com.hiddengemstore.constant.Constant.PREFIX_DISTINCTION_NAME;

/**
 * spring工具
 * @author : ZhaoJH
 **/
public class SpringUtil implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    private static ConfigurableApplicationContext configurableApplicationContext;

    /**
     * 从Spring配置中读取PREFIX_DISTINCTION_NAME属性值，如果未配置则返回默认值DEFAULT_PREFIX_DISTINCTION_NAME。
     * 配置值示例：
     * hgs-dev（开发环境）
     * hgs-prod（生产环境）
     * hgs-test（测试环境）
     * 未配置返回：
     * hgs
     * @return hgs
     */
    public static String getPrefixDistinctionName(){
        return configurableApplicationContext.getEnvironment().getProperty(PREFIX_DISTINCTION_NAME,
                DEFAULT_PREFIX_DISTINCTION_NAME);
    }
    
    @Override
    public void initialize(final @NonNull ConfigurableApplicationContext applicationContext) {
        configurableApplicationContext = applicationContext;
    }
}
