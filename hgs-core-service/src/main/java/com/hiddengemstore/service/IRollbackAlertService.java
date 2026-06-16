package com.hiddengemstore.service;

import com.hiddengemstore.entity.RollbackFailureLog;

/**
 * 回滚失败通知服务：用于发送短信/邮件告警（可插拔实现）
 * @author : ZhaoJH
 */
public interface IRollbackAlertService {

    void sendRollbackAlert(RollbackFailureLog log);
}
