package com.hiddengemstore.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 取消优惠券订单
 * @author : ZhaoJH
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CancelVoucherOrderDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 优惠券id
     * */
    @NotNull
    private Long voucherId;

}