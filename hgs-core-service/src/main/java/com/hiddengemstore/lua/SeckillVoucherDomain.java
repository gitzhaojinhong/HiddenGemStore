package com.hiddengemstore.lua;

import lombok.Data;

/**
 * lua秒杀返回数据
 * @author : ZhaoJH
 */
@Data
public class SeckillVoucherDomain {

    /**
     * 响应码(0-成功,其他-失败)
     */
    private Integer code;

    /**
     * 扣减前库存数量
     */
    private Integer beforeQty;

    /**
     * 扣减数量
     */
    private Integer deductQty;

    /**
     * 扣减后库存数量
     */
    private Integer afterQty;
}
