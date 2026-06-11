package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.UserPhone;
import com.hiddengemstore.mapper.UserPhoneMapper;
import com.hiddengemstore.service.IUserPhoneService;
import org.springframework.stereotype.Service;

@Service
public class UserPhoneServiceImpl extends ServiceImpl<UserPhoneMapper, UserPhone> implements IUserPhoneService {
}
