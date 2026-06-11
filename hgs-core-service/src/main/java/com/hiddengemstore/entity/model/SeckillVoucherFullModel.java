package com.hiddengemstore.entity.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀优惠券的全部信息
 * @author : ZhaoJH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SeckillVoucherFullModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 关联的优惠券的id
     */
    private Long voucherId;

    /**
     * 初始化库存
     */
    private Integer initStock;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 允许参与的会员等级，逗号分隔，如："1,2,3"
     */
    private String allowedLevels;

    /**
     * 最低会员等级
     */
    private Integer minLevel;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 生效时间
     */
    private LocalDateTime beginTime;

    /**
     * 失效时间
     */
    private LocalDateTime endTime;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 商铺id
     */
    private Long shopId;

}
