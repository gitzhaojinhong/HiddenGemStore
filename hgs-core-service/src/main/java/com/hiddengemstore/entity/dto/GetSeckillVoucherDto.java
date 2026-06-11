package com.hiddengemstore.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询秒杀优惠券
 * @author : ZhaoJH
 */
@Data
@Accessors(chain = true)//链式调用
@EqualsAndHashCode(callSuper = false)//生成equals和hashCode方法,callSuper = false表示只比较当前类的字段，不调用父类的比较逻辑
public class GetSeckillVoucherDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 秒杀优惠券id
     */
    @NotNull
    private Long voucherId;

}
