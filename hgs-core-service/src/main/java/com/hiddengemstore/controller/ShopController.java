package com.hiddengemstore.controller;

import com.hiddengemstore.entity.Shop;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.service.IShopService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商铺控制器
 * @author : ZhaoJH
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result<Shop> queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

}
