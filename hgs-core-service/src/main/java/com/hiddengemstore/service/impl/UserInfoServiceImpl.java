package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.UserInfo;
import com.hiddengemstore.mapper.UserInfoMapper;
import com.hiddengemstore.service.IUserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
}
