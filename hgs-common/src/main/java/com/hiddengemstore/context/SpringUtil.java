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
    
    
    public static String getPrefixDistinctionName(){
        return configurableApplicationContext.getEnvironment().getProperty(PREFIX_DISTINCTION_NAME,
                DEFAULT_PREFIX_DISTINCTION_NAME);
    }
    
    @Override
    public void initialize(final @NonNull ConfigurableApplicationContext applicationContext) {
        configurableApplicationContext = applicationContext;
    }
}
