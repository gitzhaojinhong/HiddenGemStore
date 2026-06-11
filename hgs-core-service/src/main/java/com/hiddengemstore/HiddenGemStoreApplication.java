package com.hiddengemstore;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author : ZhaoJH
 */
@EnableAspectJAutoProxy(exposeProxy = true) // 开启AOP,exposeProxy = true 暴露代理对象
@MapperScan("com.hiddengemstore.mapper")
@SpringBootApplication
public class HiddenGemStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(HiddenGemStoreApplication.class, args);
    }
}
