package com.hiddengemstore.kafka.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀券消息
 * @author : ZhaoJH
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillVoucherMessage {

    // 用户ID
    private Long userId;

    // 优惠券ID
    private Long voucherId;

    // 订单ID
    private Long orderId;

    // 追踪日志ID(用于对账和幂等)
    private Long traceId;

    // 扣减前库存数量
    private Integer beforeQty;

    // 扣减数量
    private Integer changeQty;

    // 扣减后库存数量
    private Integer afterQty;

    // 是否在回滚后自动发放(true-自动发券,false-手动领取)
    private Boolean autoIssue;
}
