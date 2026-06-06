package com.hiddengemstore.message;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import static java.util.UUID.randomUUID;

/**
 * 消息包装
 * @author : ZhaoJH
 */
@Data
@NoArgsConstructor(force = true) // 强制创建无参构造
@AllArgsConstructor
@RequiredArgsConstructor
public class MessageExtend<T> implements Serializable {
    /**
     * 序列化版本号
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务载荷 - 消息的核心内容
     * <br>
     * 【生产者侧】承载核心业务数据（如 SeckillVoucherMessage）。
     * 【消费者侧】被解析为具体的业务对象（通过 payloadType），供 doConsume 方法使用。
     * <br>
     * 实战价值：所有业务逻辑都围绕它展开。泛型设计确保了类型安全，避免了强制类型转换。
     */
    @NonNull
    private T messageBody;

    /**
     * 消息键 - 连接 Kafka 物理分区与业务逻辑实体的桥梁
     * <br>
     * 【生产者侧】决定消息分区，实现顺序性。例如：秒杀场景设置为 String.valueOf(userId)，
     *              保证同一用户的所有订单消息进入同一个分区。
     * 【消费者侧】用于业务幂等判断和日志关联。
     *              1. 幂等：配合数据库唯一约束（userId+voucherId）处理重复订单。
     *              2. 日志：所有钩子日志均打印 key，便于按用户维度追踪。
     * <br>
     * 实战价值：业务秩序与幂等的基石。
     */
    private String key;

    /**
     * 消息头 - 传递元数据和上下文的扩展容器
     * <br>
     * 【生产者侧】传递业务元数据（如 {"biz": "seckill", "traceId": "123"}）或控制信息。
     *              在 sendToDlq 时自动添加 {"dlqReason": "xxx"}。
     * 【消费者侧】框架将 Kafka 原始 Headers 转换为字符串 Map，供业务方按需获取上下文。
     * <br>
     * 实战价值：在不修改 messageBody 的前提下，灵活传递控制信息和全链路追踪上下文，极具扩展性。
     */
    private Map<String,String> headers;

    /**
     * 全局唯一标识符 - 系统可观测性的"黄金标识"
     * <br>
     * 【生产者侧】在消息创建时自动生成一个全局唯一的 UUID，随消息一起发送。
     * 【消费者侧】全链路追踪与日志关联的核心。所有钩子（before/afterConsume）均打印此 uuid。
     *              运维人员可通过此 uuid 串联：生产日志 -> Kafka Broker -> 消费日志 -> 对账日志，
     *              完整还原消息生命周期，极大简化分布式问题排查。
     * <br>
     * 实战价值：排查分布式系统问题的关键，无论消息经过多少系统节点都能精准定位。
     */
    private String uuid = randomUUID().toString();

    /**
     * 消息生产时间 - 消息生命周期管理的基准点
     * <br>
     * 【生产者侧】在消息创建时自动设置为当前时间。
     * 【消费者侧】实现消息时效性控制。在 beforeConsume 钩子中计算延迟（当前时间 - producerTime），
     *              若超过阈值（如10秒）则丢弃并触发补偿，避免处理"过期"请求。
     * <br>
     * 实战价值：支撑延迟检查、消息TTL、监控告警等场景，确保业务的时效性。
     */
    private LocalDateTime producerTime = LocalDateTime.now();

    public static <T> MessageExtend<T> of(T messageBody){
        return new MessageExtend<>(messageBody);
    }
    public static <T> MessageExtend<T> of(T messageBody, String key,Map<String,String> headers){
        MessageExtend<T> msg = new MessageExtend<>(messageBody);
        msg.setKey(key);
        msg.setHeaders(headers);
        return msg;
    }

}
