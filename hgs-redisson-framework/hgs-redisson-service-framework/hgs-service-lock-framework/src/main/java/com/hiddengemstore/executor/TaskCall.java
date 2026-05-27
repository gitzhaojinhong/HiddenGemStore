package com.hiddengemstore.executor;

/**
 * 分布式锁 方法类型执行 有返回值的业务
 * @author : ZhaoJH
 */
@FunctionalInterface// 函数式接口
public interface TaskCall<V> {
    /**
     * 执行任务
     * @return 结果
     * */
    V call();
}
