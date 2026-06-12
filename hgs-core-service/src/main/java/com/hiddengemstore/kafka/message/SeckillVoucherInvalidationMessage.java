package com.hiddengemstore.kafka.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀券缓存失效广播消息
 * @author : ZhaoJH
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillVoucherInvalidationMessage {
    // 券ID
    private Long voucherId;
    // 缓存失效原因
    private String reason;
}
