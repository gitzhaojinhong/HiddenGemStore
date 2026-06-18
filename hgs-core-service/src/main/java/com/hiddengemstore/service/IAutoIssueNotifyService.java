package com.hiddengemstore.service;

/**
 * 自动发券成功后的用户通知服务接口
 * @author : ZhaoJH
 */
public interface IAutoIssueNotifyService {

    void sendAutoIssueNotify(Long voucherId, Long userId, Long orderId);
}