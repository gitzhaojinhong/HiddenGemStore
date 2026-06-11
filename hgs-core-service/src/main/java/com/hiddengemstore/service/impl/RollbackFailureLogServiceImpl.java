package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.RollbackFailureLog;
import com.hiddengemstore.mapper.RollbackFailureLogMapper;
import com.hiddengemstore.service.IRollbackFailureLogService;
import org.springframework.stereotype.Service;

@Service
public class RollbackFailureLogServiceImpl extends ServiceImpl<RollbackFailureLogMapper, RollbackFailureLog> implements IRollbackFailureLogService {
}
