package com.hiddengemstore.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对账日志
 * @author : ZhaoJH
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class VoucherReconcileLogDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单id
     */
    private Long orderId;

    /**
     * 下单的用户id
     */
    private Long userId;

    /**
     * 购买的代金券id
     */
    private Long voucherId;

    /**
     * Kafka消息uuid
     */
    private String messageId;

    /**
     * 差异说明
     */
    private String detail;

    /**
     * 改变之前库存数量
     */
    private Integer beforeQty;

    /**
     * 本次改变数量
     */
    private Integer changeQty;

    /**
     * 改变之后库存数量
     */
    private Integer afterQty;

    /**
     * 追踪唯一标识
     */
    private Long traceId;

    /**
     * 记录类型：-1扣减，1恢复
     */
    private Integer logType;

    /**
     * 业务类型：1创建订单成功，2创建订单超时，3创建订单失败，4主动取消
     */
    private Integer businessType;
}
