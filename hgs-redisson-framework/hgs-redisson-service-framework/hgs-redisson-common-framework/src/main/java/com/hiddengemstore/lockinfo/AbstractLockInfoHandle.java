package com.hiddengemstore.lockinfo;

import cn.hutool.core.util.StrUtil;
import com.hiddengemstore.context.SpringUtil;
import com.hiddengemstore.lockinfo.parser.ExtParameterNameDiscoverer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 锁信息抽象类
 * 提供锁名称生成的通用逻辑，支持SpEL表达式解析方法参数
 * 子类需实现getLockPrefixName()方法定义锁的前缀名称
 * @author : ZhaoJH
 */
@Slf4j
public abstract class AbstractLockInfoHandle implements LockInfoHandle{
    // 锁分布式ID名称前缀常量
    private static final String LOCK_DISTRIBUTE_ID_NAME_PREFIX = "LOCK_DISTRIBUTE_ID";

    // 参数名称发现器，用于获取方法参数的实际名称
    private final ParameterNameDiscoverer nameDiscoverer = new ExtParameterNameDiscoverer();

    // SpEL表达式解析器，用于解析注解中的表达式（如#userId、#order.id等）
    private final ExpressionParser parser = new SpelExpressionParser();


    /**
     * 获取锁前缀名称（由子类实现）
     * @return 锁前缀名称
     */
    protected abstract String getLockPrefixName();

    /**
     * 生成完整的锁名称（用于AOP切面）
     * 格式：{应用前缀}-{锁类型前缀}:{锁名}:{解析后的keys}
     * @param joinPoint AOP切点对象
     * @param lockName 锁的名称
     * @param keys 锁的key数组，支持SpEL表达式
     * @return 完整的锁名称
     */
    @Override
    public String getLockName(JoinPoint joinPoint, String lockName, String[] keys) {
        return SpringUtil.getPrefixDistinctionName() + "-" + getLockPrefixName() + ":" + lockName + getRelKey(joinPoint, keys);
    }

    /**
     * 简单生成锁名称（用于编程式调用）
     * 不支持SpEL表达式，直接使用传入的keys
     * 格式：{应用前缀}-LOCK_DISTRIBUTE_ID:{锁名}:{keys}
     * @param lockName 锁的名称
     * @param keys 锁的key数组
     * @return 锁名称
     */
    @Override
    public String simpleGetLockName(String lockName, String[] keys) {
        List<String> definitionKeyList = new ArrayList<>();
        for (String key : keys) {
            if (StrUtil.isNotEmpty(key)) {
                definitionKeyList.add(key);
            }
        }
        return SpringUtil.getPrefixDistinctionName() + "-" +
                LOCK_DISTRIBUTE_ID_NAME_PREFIX + ":" + lockName + ":" + String.join(":", definitionKeyList);

    }


    /**
     * 解析并拼接锁的key部分
     * @param joinPoint AOP切点对象
     * @param keys key数组，可能包含SpEL表达式
     * @return 解析后的key字符串，格式为":key1:key2"
     */
    private String getRelKey(JoinPoint joinPoint, String[] keys){
        Method method = getMethod(joinPoint);
        List<String> definitionKeys = getSpElKey(keys, method, joinPoint.getArgs());
        //第一个 ":" 作为前缀分隔符，连接前面的部分（name）和后面的 keys; 第二个 ":" 作为keys 之间的分隔符，当有多个 key 时用于拼接
        return ":" + String.join(":", definitionKeys);
    }
    /**
     * 获取目标方法对象
     * 处理接口代理的情况，从实现类中获取真实方法
     * @param joinPoint AOP切点对象
     * @return 目标方法
     */
    private Method getMethod(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.getDeclaringClass().isInterface()) {
            try {
                method = joinPoint.getTarget().getClass().getDeclaredMethod(signature.getName(),
                        method.getParameterTypes());
            } catch (Exception e) {
                log.error("get method error ",e);
            }
        }
        return method;
    }
    /**
     * 解析SpEL表达式，提取方法参数值
     * 支持表达式如：#userId、#user.id、#p0等
     * @param definitionKeys 定义的key数组，可能包含SpEL表达式
     * @param method 目标方法
     * @param parameterValues 方法参数值数组
     * @return 解析后的key列表
     */
    private List<String> getSpElKey(String[] definitionKeys, Method method, Object[] parameterValues) {
        List<String> definitionKeyList = new ArrayList<>();
        for (String definitionKey : definitionKeys) {
            if (!ObjectUtils.isEmpty(definitionKey)) {
                // 创建SpEL评估上下文，null表示不使用rootObject
                EvaluationContext context = new MethodBasedEvaluationContext(null, method, parameterValues, nameDiscoverer);

                Object objKey = parser.parseExpression(definitionKey).getValue(context);
                definitionKeyList.add(ObjectUtils.nullSafeToString(objKey));
            }
        }
        return definitionKeyList;
    }



}
