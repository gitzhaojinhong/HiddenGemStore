package com.hiddengemstore.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.context.SpringUtil;
import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.entity.UserInfo;
import com.hiddengemstore.entity.VoucherOrder;
import com.hiddengemstore.entity.VoucherOrderRouter;
import com.hiddengemstore.entity.VoucherReconcileLog;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
import com.hiddengemstore.enums.*;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.kafka.producer.SeckillVoucherProducer;
import com.hiddengemstore.lua.SeckillVoucherDomain;
import com.hiddengemstore.lua.SeckillVoucherOperate;
import com.hiddengemstore.mapper.VoucherOrderMapper;
import com.hiddengemstore.mapper.VoucherOrderRouterMapper;
import com.hiddengemstore.mapper.VoucherReconcileLogMapper;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.hiddengemstore.service.ISeckillVoucherService;
import com.hiddengemstore.service.IUserInfoService;
import com.hiddengemstore.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hiddengemstore.constant.Constant.SECKILL_VOUCHER_TOPIC;
import static com.hiddengemstore.constant.RepeatExecuteLimitConstants.SECKILL_VOUCHER_ORDER;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private SeckillVoucherOperate seckillVoucherOperate;
    @Resource
    private SeckillVoucherProducer seckillVoucherProducer;
    @Resource
    private RedisCache redisCache;
    @Resource
    private VoucherOrderRouterMapper voucherOrderRouterMapper;
    @Resource
    private VoucherReconcileLogMapper voucherReconcileLogMapper;


    @Override
    public Result<Long> seckillVoucher(Long voucherId, Long userId) {
        // 获取秒杀卷信息
        SeckillVoucherFullModel seckillVoucherFullModel = seckillVoucherService.queryByVoucherId(voucherId);
        // 加载库存到redis
        seckillVoucherService.loadVoucherStock(voucherId);
        // 验证用户等级
        verifyUserLevel(seckillVoucherFullModel, userId);

        long orderId = snowflakeIdGenerator.nextId();
        long traceId = snowflakeIdGenerator.nextId();
        // 执行lua脚本需要的key（单槽位Hash Tag键，不分片）
        List<String> keys = ListUtil.of(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId).getRelKey()
        );
        //执行lua中需要的数据：
        // args[0]: voucherId - 秒杀优惠券ID
        // args[1]: userId - 用户ID
        // args[2]: beginTime - 秒杀开始时间(毫秒时间戳)
        // args[3]: endTime - 秒杀结束时间(毫秒时间戳)
        // args[4]: status - 优惠券状态(1-上架,2-下架,3-过期)
        // args[5]: orderId - 订单ID
        // args[6]: traceId - 追踪日志ID(用于对账和幂等)
        // args[7]: logType - 操作类型(扣减库存)
        // args[8]: ttlSeconds - 追踪日志的TTL(结束时间+1天,单位秒)
        String[] args = new String[9];
        args[0] = voucherId.toString();
        args[1] = userId.toString();
        args[2] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getBeginTime()));
        args[3] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getEndTime()));
        args[4] = String.valueOf(seckillVoucherFullModel.getStatus());
        args[5] = String.valueOf(orderId);
        args[6] = String.valueOf(traceId);
        args[7] = String.valueOf(LogType.DEDUCT.getCode());
        long secondsUntilEnd = Duration.between(LocalDateTimeUtil.now(), seckillVoucherFullModel.getEndTime()).getSeconds();
        long ttlSeconds = Math.max(1L, secondsUntilEnd + Duration.ofDays(1).getSeconds());
        args[8] = String.valueOf(ttlSeconds);
        // 执行lua脚本
        SeckillVoucherDomain seckillVoucherDomain = seckillVoucherOperate.execute(
                keys,
                args
        );
        if (!seckillVoucherDomain.getCode().equals(BaseCode.SUCCESS.getCode())) {
            throw new HGSFrameException(Objects.requireNonNull(BaseCode.getRc(seckillVoucherDomain.getCode())));
        }

        // 发送Kafka消息
        // 组装消息
        SeckillVoucherMessage seckillVoucherMessage = new SeckillVoucherMessage(
                userId,
                voucherId,
                orderId,
                traceId,
                seckillVoucherDomain.getBeforeQty(),
                seckillVoucherDomain.getDeductQty(),
                seckillVoucherDomain.getAfterQty(),
                Boolean.FALSE
        );
        seckillVoucherProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_TOPIC,
                seckillVoucherMessage);

        return Result.ok(orderId);

    }

    @Override
    @RepeatExecuteLimit(name = SECKILL_VOUCHER_ORDER, keys = {"#message.uuid"})//以消息唯一标识 uuid 做幂等防重
    @Transactional(rollbackFor = Exception.class)// 失败即回滚库内变更
    public void createVoucherOrder(MessageExtend<SeckillVoucherMessage> message) {
        //获取消息体
        SeckillVoucherMessage messageBody = message.getMessageBody();
        Long userId = messageBody.getUserId();
        //根据优惠券id和用户id查询是否已经存在正常的订单：用户+券+订单状态
        VoucherOrder normalVoucherOrder = lambdaQuery()
                .eq(VoucherOrder::getVoucherId, messageBody.getVoucherId())
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getStatus, OrderStatus.NORMAL.getCode())
                .one();
        //如果存在，则直接结束运行
        if (Objects.nonNull(normalVoucherOrder)) {
            log.warn("已存在此订单，voucherId：{},userId：{}", normalVoucherOrder.getVoucherId(), userId);
            throw new HGSFrameException(BaseCode.VOUCHER_ORDER_EXIST);
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                // set stock = stock - 1
                .setSql("stock = stock - 1")
                // where id = ? and stock > 0
                .eq("voucher_id", messageBody.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败：触发消息侧回滚Redis数据
            throw new HGSFrameException("优惠券库存不足！优惠券id:" + messageBody.getVoucherId());
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder()
                .setId(messageBody.getOrderId())
                .setUserId(messageBody.getUserId())
                .setVoucherId(messageBody.getVoucherId())
                .setCreateTime(LocalDateTimeUtil.now());
        save(voucherOrder);
        //创建订单路由
        VoucherOrderRouter voucherOrderRouter = new VoucherOrderRouter()
                .setId(snowflakeIdGenerator.nextId())
                .setOrderId(voucherOrder.getId())
                .setUserId(userId)
                .setVoucherId(voucherOrder.getVoucherId())
                .setCreateTime(LocalDateTimeUtil.now())
                .setUpdateTime(LocalDateTimeUtil.now());
        voucherOrderRouterMapper.insert(voucherOrderRouter);
        //订单存放到redis
        redisCache.set(RedisKeyBuild.createRedisKey(
                        RedisKeyManage.DB_SECKILL_ORDER_KEY, messageBody.getOrderId()),
                voucherOrder,
                60,
                TimeUnit.SECONDS
        );
        //对账日志：一致-消费成功
        VoucherReconcileLog voucherReconcileLog=new VoucherReconcileLog()
                .setId(snowflakeIdGenerator.nextId())
                .setOrderId(messageBody.getOrderId())
                .setUserId(messageBody.getUserId())
                .setVoucherId(messageBody.getVoucherId())
                .setMessageId(message.getUuid())
                .setBusinessType(BusinessType.SUCCESS.getCode())
                .setDetail("order created")
                .setTraceId(messageBody.getTraceId())
                .setLogType(LogType.DEDUCT.getCode())
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setBeforeQty(messageBody.getBeforeQty())
                .setChangeQty(messageBody.getChangeQty())
                .setAfterQty(messageBody.getAfterQty());
        voucherReconcileLogMapper.insert(voucherReconcileLog);
    }

    /**
     * 校验用户是否满足参与券活动的人群规则：
     * - allowedLevels: 逗号分隔的会员等级白名单（示例："1,2,3"）
     * - minLevel: 允许参与的最低会员等级（含）
     * 规则说明：
     * - 若两者均为空，则视为无门槛，直接通过
     * - 若存在 allowedLevels，则用户等级需在白名单中
     * - 若存在 minLevel，则用户等级需 >= minLevel
     * - 两者并存时，需同时满足（先白名单，再最低等级）
     * 校验失败将抛出业务异常以终止抢券
     */
    public void verifyUserLevel(SeckillVoucherFullModel seckillVoucherFullModel, Long userId) {
        // 从券模型读取允许的等级白名单字符串（可能为空）
        String allowedLevelsStr = seckillVoucherFullModel.getAllowedLevels();
        // 从券模型读取最低等级限制（可能为空）
        Integer minLevel = seckillVoucherFullModel.getMinLevel();
        // 是否存在任何等级规则：白名单或最低等级其一即可视为有门槛
        boolean hasLevelRule = StrUtil.isNotBlank(allowedLevelsStr) || Objects.nonNull(minLevel);
        // 无等级规则时直接放行，避免不必要的数据库查询
        if (!hasLevelRule) {
            return;
        }
        // 查询用户基本信息（含会员等级等）
        UserInfo userInfo = userInfoService.getByUserId(userId);
        // 用户不存在直接抛错，防止后续空指针并阻断抢券
        if (Objects.isNull(userInfo)) {
            throw new HGSFrameException(BaseCode.USER_NOT_EXIST);
        }
        // allowed 标识当前用户是否通过规则校验，默认通过
        boolean allowed = true;
        // 当前用户的会员等级（可能为空）
        Integer level = userInfo.getLevel();
        // 处理 allowedLevels 白名单规则
        if (StrUtil.isNotBlank(allowedLevelsStr)) {
            try {
                // 将逗号分隔的字符串解析为去空格的整型集合
                Set<Integer> allowedLevels = Arrays.stream(allowedLevelsStr.split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .map(Integer::valueOf)
                        .collect(Collectors.toSet());
                // 白名单非空时要求用户等级在集合之中
                if (CollectionUtil.isNotEmpty(allowedLevels)) {
                    allowed = allowedLevels.contains(level);
                }
            } catch (Exception parseEx) {
                // 解析失败记录日志但不中断流程，保持 allowed = true 让后续 minLevel 规则继续判断
                log.warn("allowedLevels 解析失败, voucherId={}, raw={}",
                        seckillVoucherFullModel.getVoucherId(),
                        allowedLevelsStr, parseEx);
            }
        }
        // 处理最低等级 minLevel 规则：仅当之前仍允许时再判断
        if (allowed && Objects.nonNull(minLevel)) {
            // 用户等级不为空且 >= 最低等级方可通过
            allowed = Objects.nonNull(level) && level >= minLevel;
        }
        // 最终不满足规则则抛出业务异常，终止本次抢券
        if (!allowed) {
            throw new HGSFrameException("当前会员级别不满足参与条件");
        }
    }
}
