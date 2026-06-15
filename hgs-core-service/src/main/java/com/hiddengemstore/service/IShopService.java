package com.hiddengemstore.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiddengemstore.entity.Shop;
import com.hiddengemstore.entity.dto.Result;

public interface IShopService extends IService<Shop> {
    Result<Shop> queryById(Long id);
}
