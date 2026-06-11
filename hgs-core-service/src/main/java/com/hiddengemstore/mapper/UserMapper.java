package com.hiddengemstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiddengemstore.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
