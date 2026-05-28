package com.hiddengemstore.api.consumer;

/**
 * 消费者入口,接口
 * @author : ZhaoJH
 */
public interface DelayQueueConsumer {
    /**
     * 消费者执行方法
     * @param content 消息内容
     */
    void execute(String  content);

    /**
     * 指定消费者主题
     * @return 主题
     */
    String topic();
}
