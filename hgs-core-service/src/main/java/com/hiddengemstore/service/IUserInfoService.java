package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.UserInfo;

public interface IUserInfoService extends IService<UserInfo> {
    UserInfo getByUserId(Long userId);
}
