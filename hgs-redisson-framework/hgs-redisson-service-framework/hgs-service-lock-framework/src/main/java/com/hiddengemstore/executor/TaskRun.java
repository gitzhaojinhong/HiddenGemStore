package com.hiddengemstore.executor;

/**
 * 分布式锁 方法类型执行 无返回值的业务
 * @author : ZhaoJH
 */
@FunctionalInterface// 函数式接口
public interface TaskRun {
    /**
     * 执行任务
     * */
    void run();
}
