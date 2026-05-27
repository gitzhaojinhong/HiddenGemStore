package com.hiddengemstore.repeatexecutelimit.annotion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 重复执行限制注解->幂等
 * @author : ZhaoJH
 */
@Target(value = {ElementType.TYPE,ElementType.METHOD})// 范围：类、方法
@Retention(value = RetentionPolicy.RUNTIME)// 运行时
public @interface RepeatExecuteLimit {
    /**
     * 业务名称
     */
    String name() default "";
    /**
     * 幂等唯一标识
     */
    String[] keys();
    /**
     * 幂等保持时间
     */
    long durationTime() default 0L;
    /**
     * 幂等提示信息
     */
    String message() default "提交频繁，请稍后重试";

}
