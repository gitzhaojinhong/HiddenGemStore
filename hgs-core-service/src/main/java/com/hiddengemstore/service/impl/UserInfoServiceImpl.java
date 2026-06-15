package com.hiddengemstore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.entity.UserInfo;
import com.hiddengemstore.enums.BaseCode;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.annotation.ServiceLock;
import com.hiddengemstore.mapper.UserInfoMapper;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.IUserInfoService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_USER_INFO_LOCK;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {


    @Resource
    private RedisCache redisCache;
    @Override
    @ServiceLock(lockType= LockType.Read,name = UPDATE_USER_INFO_LOCK,keys = {"#userId"})
    public UserInfo getByUserId(Long userId){
        UserInfo userInfo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId), UserInfo.class);
        if (Objects.nonNull(userInfo)){
            return userInfo;
        }
        userInfo = lambdaQuery().eq(UserInfo::getUserId, userId).one();
        if (Objects.isNull(userInfo)) {
            throw new HGSFrameException(BaseCode.USER_NOT_EXIST);
        }
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId), userInfo);
        return userInfo;
    }
}
