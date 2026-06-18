package com.hiddengemstore.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.context.SpringUtil;
import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.entity.*;
import com.hiddengemstore.entity.dto.CancelVoucherOrderDto;
import com.hiddengemstore.entity.dto.Result;
import com.hiddengemstore.entity.dto.VoucherReconcileLogDto;
import com.hiddengemstore.entity.model.SeckillVoucherFullModel;
import com.hiddengemstore.enums.*;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.kafka.producer.SeckillVoucherProducer;
import com.hiddengemstore.kafka.rollbackredis.RedisVoucherDataRollback;
import com.hiddengemstore.lua.SeckillVoucherDomain;
import com.hiddengemstore.lua.SeckillVoucherOperate;
import com.hiddengemstore.mapper.*;
import com.hiddengemstore.message.MessageExtend;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.hiddengemstore.service.ISeckillVoucherService;
import com.hiddengemstore.service.IUserInfoService;
import com.hiddengemstore.service.IVoucherOrderService;
import com.hiddengemstore.uitls.UserHolder;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private RedisVoucherDataRollback redisVoucherData;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    public static final ThreadPoolExecutor SECKILL_ORDER_EXECUTOR =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1024),
                    new NamedThreadFactory("seckill-order-", false),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final boolean daemon;
        private final AtomicInteger index = new AtomicInteger(1);

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, namePrefix + index.getAndIncrement());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    log.error("未捕获异常，线程={}, err={}", thread.getName(), ex.getMessage(), ex)
            );
            return t;
        }
    }
    @PreDestroy//销毁线程池
    private void destroy(){
        try {
            SECKILL_ORDER_EXECUTOR.shutdown();
            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                SECKILL_ORDER_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SECKILL_ORDER_EXECUTOR.shutdownNow();
        }
    }












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
            // 抛出异常，异常的错误码与状态码一致，这样消费失败后执行afterConsumeFailure失败钩子回滚时，就不需要从已购买优惠卷集合中移除用户
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
        VoucherReconcileLog voucherReconcileLog = new VoucherReconcileLog()
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

    /**
     * 取消秒杀券订单
     * 业务流程：
     * 1. 验证订单是否存在且属于当前用户
     * 2. 验证秒杀券是否存在
     * 3. 更新订单状态为已取消
     * 4. 记录对账日志（库存恢复）
     * 5. 回滚数据库库存
     * 6. 回滚Redis数据（库存、用户集合等）
     * 7. 删除用户订阅状态缓存
     * 8. 更新店铺每日购买排行榜
     * 9. 尝试自动发券给下一个订阅用户
     *
     * @param cancelVoucherOrderDto 取消订单请求参数（包含券ID）
     * @return 取消是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto) {
        // 查询当前用户的正常状态订单，验证订单是否存在
        VoucherOrder voucherOrder = lambdaQuery()
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .eq(VoucherOrder::getVoucherId, cancelVoucherOrderDto.getVoucherId())
                .eq(VoucherOrder::getStatus, OrderStatus.NORMAL.getCode())
                .one();
        // 订单不存在，抛出异常
        if (Objects.isNull(voucherOrder)) {
            throw new HGSFrameException(BaseCode.SECKILL_VOUCHER_ORDER_NOT_EXIST);
        }
        // 查询秒杀券信息，验证券是否存在
        SeckillVoucher seckillVoucher = seckillVoucherService.lambdaQuery()
                .eq(SeckillVoucher::getVoucherId, cancelVoucherOrderDto.getVoucherId())
                .one();
        // 秒杀券不存在，抛出异常
        if (Objects.isNull(seckillVoucher)) {
            throw new HGSFrameException(BaseCode.SECKILL_VOUCHER_NOT_EXIST);
        }
        // 更新订单状态为CANCEL（已取消），并更新修改时间
        boolean updateResult = lambdaUpdate().set(VoucherOrder::getStatus, OrderStatus.CANCEL.getCode())
                .set(VoucherOrder::getUpdateTime, LocalDateTimeUtil.now())
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .eq(VoucherOrder::getVoucherId, cancelVoucherOrderDto.getVoucherId())
                .update();
        // 生成追踪ID，用于对账和幂等
        long traceId = snowflakeIdGenerator.nextId();
        // 构建对账日志DTO，记录库存恢复操作
        VoucherReconcileLogDto voucherReconcileLogDto = new VoucherReconcileLogDto()
                .setOrderId(voucherOrder.getId())
                .setUserId(voucherOrder.getUserId())
                .setVoucherId(voucherOrder.getVoucherId())
                .setDetail("cancel voucher order ")
                .setBeforeQty(seckillVoucher.getStock())
                .setChangeQty(1)
                .setAfterQty(seckillVoucher.getStock() + 1)
                .setTraceId(traceId)
                .setLogType(LogType.RESTORE.getCode())
                .setBusinessType(BusinessType.CANCEL.getCode());
        // 插入对账日志，判断是否插入成功（受影响行数>0）
        boolean saveReconcileLogResult = voucherReconcileLogMapper.insert(new VoucherReconcileLog()
                .setId(snowflakeIdGenerator.nextId())
                .setOrderId(voucherReconcileLogDto.getOrderId())
                .setUserId(voucherReconcileLogDto.getUserId())
                .setVoucherId(voucherReconcileLogDto.getVoucherId())
                .setMessageId(voucherReconcileLogDto.getMessageId())
                .setBusinessType(voucherReconcileLogDto.getBusinessType())
                .setDetail(voucherReconcileLogDto.getDetail())
                .setTraceId(voucherReconcileLogDto.getTraceId())
                .setLogType(voucherReconcileLogDto.getLogType())
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setBeforeQty(voucherReconcileLogDto.getBeforeQty())
                .setChangeQty(voucherReconcileLogDto.getChangeQty())
                .setAfterQty(voucherReconcileLogDto.getAfterQty())) > 0;

        // 回滚数据库库存（stock = stock + 1），判断是否执行成功
        boolean rollbackStockResult = seckillVoucherMapper.rollbackStock(cancelVoucherOrderDto.getVoucherId())>0;

        // 综合判断三个操作是否都成功
        Boolean result = updateResult && saveReconcileLogResult && rollbackStockResult;
        // 如果全部成功，执行后续清理和补偿操作
        if (result) {
            // 回滚Redis中的秒杀数据（库存、用户集合、订单缓存等）
            redisVoucherData.rollbackRedisVoucherData(
                    SeckillVoucherOrderOperate.YES,
                    traceId,
                    voucherOrder.getVoucherId(),
                    voucherOrder.getUserId(),
                    voucherOrder.getId(),
                    seckillVoucher.getStock(),
                    1,
                    seckillVoucher.getStock() + 1
            );
            // 删除用户在订阅状态Hash中的缓存，避免脏数据
            redisCache.delForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY,
                            cancelVoucherOrderDto.getVoucherId()),
                    String.valueOf(voucherOrder.getUserId()));
            // 查询券信息，获取店铺ID
            Voucher voucher = voucherMapper.selectById(voucherOrder.getVoucherId());
            // 如果券存在，更新店铺每日购买排行榜
            if (Objects.nonNull(voucher)) {
                // 格式化日期为yyyyMMdd格式
                String day = voucherOrder.getCreateTime().format(DateTimeFormatter.BASIC_ISO_DATE);
                // 构建店铺每日购买排行榜的Redis Key
                RedisKeyBuild dailyKey = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.SECKILL_SHOP_TOP_BUYERS_DAILY_TAG_KEY,
                        voucher.getShopId(),
                        day
                );
                // 减少该用户在排行榜中的购买次数（-1）
                redisCache.incrementScoreForSortedSet(dailyKey, String.valueOf(voucherOrder.getUserId()), -1.0);
            }

            // 回滚成功后，尝试自动发券给最早的订阅用户
            try {
                autoIssueVoucherToEarliestSubscriber(
                        voucherOrder.getVoucherId(),
                        voucherOrder.getUserId()
                );
            } catch (Exception e) {
                // 自动发券失败仅记录日志，不影响取消流程
                log.warn("自动发券失败，voucherId={}, err=\n{}", voucherOrder.getVoucherId(), e.getMessage());
            }
        }
        // 返回取消操作结果
        return result;
    }
    /**
     * 自动发券给最早的订阅用户
     * 业务流程：
     * 1. 查询秒杀券信息并验证有效性
     * 2. 加载库存到Redis
     * 3. 查找最早的符合条件的候选用户
     * 4. 向候选用户发券
     * 说明：
     * - 不修改订阅集合与状态，成功下单后用户将出现在已购集合，状态查询会返回SUCCESS；
     * - 为避免重复，筛选时排除已购用户与当前取消用户；
     * - 采用范围批量读取前N条并按score最小选取候选，避免由于Set去序导致的顺序丢失。
     * @param voucherId 秒杀券ID
     * @param excludeUserId 排除的用户ID（通常是刚取消订单的用户）
     * @return 是否成功发券
     */
    @Override
    public boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId) {
        // 查询秒杀券完整信息（包含人群规则）
        SeckillVoucherFullModel seckillVoucherFullModel = seckillVoucherService.queryByVoucherId(voucherId);
        // 校验券数据完整性（开始/结束时间必须存在）
        if (Objects.isNull(seckillVoucherFullModel)
                ||
                Objects.isNull(seckillVoucherFullModel.getBeginTime())
                ||
                Objects.isNull(seckillVoucherFullModel.getEndTime())) {
            // 数据不完整时终止自动发券
            return false;
        }
        // 需要再加载一次库存，防止修改数据或者对账执行时将此redis中的库存删除
        seckillVoucherService.loadVoucherStock(voucherId);
        // 在订阅ZSET中查找最早且未购的候选用户（排除本次取消用户）
        String candidateUserIdStr = findEarliestCandidate(voucherId, excludeUserId);
        // 没有候选用户则结束流程
        if (StrUtil.isBlank(candidateUserIdStr)) {
            return false;
        }
        // 执行扣减与消息下发，并在成功后移除候选的ZSET位置
        return issueToCandidate(voucherId, candidateUserIdStr, seckillVoucherFullModel);
    }

    /**
     * 查找最早的符合条件的候选用户
     * 查找逻辑：
     * 1. 从订阅ZSET中按加入时间顺序获取用户
     * 2. 跳过已购买用户
     * 3. 跳过被排除的用户
     * 4. 返回第一个符合条件的用户
     *
     * @param voucherId 秒杀券ID
     * @param excludeUserId 需要排除的用户ID
     * @return 候选用户ID字符串，未找到返回null
     */
    private String findEarliestCandidate(final Long voucherId, final Long excludeUserId) {
        // 构建订阅ZSET的Redis Key（member为用户ID，score为加入时间戳）
        RedisKeyBuild subscribeZSetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, voucherId);
        // 构建已购买用户集合的Redis Key
        RedisKeyBuild purchasedSetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId);

        // 每次查询1条记录，逐页遍历
        final long pageCount = 1L;
        long offset = 0L;
        // 循环遍历ZSET，直到找到符合条件的用户或遍历完所有用户
        while (true) {
            // 从ZSET中按分数范围查询，分页获取用户
            Set<ZSetOperations.TypedTuple<String>> page = redisCache.rangeByScoreWithScoreForSortedSet(
                    subscribeZSetKey,               //ZSET 的 Redis Key，member 是用户 ID，score 是订阅时间戳
                    Double.NEGATIVE_INFINITY,       //score 范围不设限制，即查询所有分数（所有时间戳）的成员
                    Double.POSITIVE_INFINITY,
                    offset,                         //从第几条记录开始取（分页偏移量）-> 0
                    pageCount,                      //每次只取 1 条记录
                    String.class                    //返回的 member 类型是字符串（用户 ID）
            );
            // 如果本页没有数据，说明遍历完毕，返回null
            if (CollectionUtil.isEmpty(page)) {
                return null;
            }
            // 获取本页的第一个元素
            ZSetOperations.TypedTuple<String> tuple = page.iterator().next();
            // 如果tuple或value为空，跳过继续下一个
            if (Objects.isNull(tuple) || Objects.isNull(tuple.getValue())) {
                offset++;
                continue;
            }
            // 获取用户ID字符串
            String uidStr = tuple.getValue();
            // 如果uidStr为空，跳过继续下一个
            if (StrUtil.isBlank(uidStr)) {
                offset++;
                continue;
            }
            // 排除本次取消的用户
            if (Objects.nonNull(excludeUserId) && Objects.equals(uidStr, String.valueOf(excludeUserId))) {
                offset++;
                continue;
            }
            // 检查用户是否已购买
            Boolean purchased = redisCache.isMemberForSet(purchasedSetKey, uidStr);
            // 如果用户已购买，跳过继续下一个
            if (BooleanUtil.isTrue(purchased)) {
                offset++;
                continue;
            }
            // 找到符合条件的用户，返回其ID
            return uidStr;
        }
    }
    /**
     * 向候选用户发券
     * 发券流程：
     * 1. 验证用户等级是否符合券的人群规则
     * 2. 构建Lua脚本所需的Keys和Args
     * 3. 执行Lua脚本扣减库存
     * 4. 发送Kafka消息创建订单
     *
     * @param voucherId 秒杀券ID
     * @param candidateUserIdStr 候选用户ID字符串
     * @param seckillVoucherFullModel 秒杀券完整信息
     * @return 是否成功发券
     */
    private boolean issueToCandidate(final Long voucherId,
                                     final String candidateUserIdStr,
                                     final SeckillVoucherFullModel seckillVoucherFullModel) {
        // 将用户ID字符串转换为Long类型
        Long candidateUserId = Long.valueOf(candidateUserIdStr);
        // 尝试验证用户等级是否符合券的人群规则
        try {
            verifyUserLevel(seckillVoucherFullModel, candidateUserId);
        } catch (Exception e) {
            // 用户不满足人群规则，记录日志并返回失败
            log.info("候选用户不满足人群规则，自动发券跳过。voucherId={}, userId={}", voucherId, candidateUserId);
            return false;
        }
        // 构建Lua脚本所需的Redis Keys（库存、用户集合、追踪日志）
        List<String> keys = buildSeckillKeys(voucherId);
        // 生成订单ID
        long orderId = snowflakeIdGenerator.nextId();
        // 生成追踪ID，用于对账和幂等
        long traceId = snowflakeIdGenerator.nextId();
        // 构建Lua脚本所需的参数Args
        String[] args = buildSeckillArgs(voucherId, candidateUserIdStr, seckillVoucherFullModel, orderId, traceId);
        // 执行Lua脚本，扣减库存并记录追踪日志
        SeckillVoucherDomain domain = seckillVoucherOperate.execute(keys, args);
        // 如果Lua脚本执行失败，记录日志并返回失败
        if (!Objects.equals(domain.getCode(), BaseCode.SUCCESS.getCode())) {
            log.info("自动发券Lua扣减失败，code={}, voucherId={}, userId={}", domain.getCode(), voucherId, candidateUserId);
            return false;
        }
        // 构建Kafka消息体
        SeckillVoucherMessage message = new SeckillVoucherMessage(
                candidateUserId,
                voucherId,
                orderId,
                traceId,
                domain.getBeforeQty(),
                domain.getDeductQty(),
                domain.getAfterQty(),
                Boolean.TRUE
        );
        // 发送Kafka消息，异步创建订单
        seckillVoucherProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_TOPIC,
                message
        );
        // 注意：不在此处移除订阅ZSET成员，也不记录“成功”日志。
        // 订阅ZSET的移除应在消息消费成功并创建订单后进行，避免发送或消费失败导致丢号。

        // 发券成功，返回true
        return true;
    }
    /**
     * 构建秒杀Lua脚本所需的Redis Keys
     * 包含：库存Key、用户集合Key、追踪日志Key
     * 使用同槽位Hash Tag确保在Redis Cluster中落在同一个slot
     *
     * @param voucherId 秒杀券ID
     * @return Redis Keys列表
     */
    private List<String> buildSeckillKeys(final Long voucherId) {
        // 构建库存Key，使用Hash Tag {voucherId}
        String stockKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey();
        // 构建已购买用户集合Key，使用Hash Tag {voucherId}
        String userKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey();
        // 构建追踪日志Key，使用Hash Tag {voucherId}
        String traceKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId).getRelKey();
        // 返回Keys列表
        return ListUtil.of(stockKey, userKey, traceKey);
    }
    /**
     * 构建秒杀Lua脚本所需的参数Args
     * 参数说明：
     * args[0]: voucherId - 秒杀券ID
     * args[1]: userId - 用户ID
     * args[2]: beginTime - 秒杀开始时间（毫秒时间戳）
     * args[3]: endTime - 秒杀结束时间（毫秒时间戳）
     * args[4]: status - 券状态
     * args[5]: orderId - 订单ID
     * args[6]: traceId - 追踪ID
     * args[7]: logType - 日志类型（扣减库存）
     * args[8]: ttlSeconds - 追踪日志TTL
     *
     * @param voucherId 秒杀券ID
     * @param userIdStr 用户ID字符串
     * @param seckillVoucherFullModel 秒杀券完整信息
     * @param orderId 订单ID
     * @param traceId 追踪ID
     * @return Lua脚本参数字符串数组
     */
    private String[] buildSeckillArgs(final Long voucherId,
                                      final String userIdStr,
                                      final SeckillVoucherFullModel seckillVoucherFullModel,
                                      final long orderId,
                                      final long traceId) {
        // 初始化参数数组，共9个参数
        String[] args = new String[9];
        // args[0]: 秒杀券ID
        args[0] = voucherId.toString();
        // args[1]: 用户ID
        args[1] = userIdStr;
        // args[2]: 秒杀开始时间（毫秒时间戳）
        args[2] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getBeginTime()));
        // args[3]: 秒杀结束时间（毫秒时间戳）
        args[3] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getEndTime()));
        // args[4]: 券状态（1-上架，2-下架，3-过期）
        args[4] = String.valueOf(seckillVoucherFullModel.getStatus());
        // args[5]: 订单ID
        args[5] = String.valueOf(orderId);
        // args[6]: 追踪ID，用于对账和幂等
        args[6] = String.valueOf(traceId);
        // args[7]: 日志类型（扣减库存）
        args[7] = String.valueOf(LogType.DEDUCT.getCode());
        // args[8]: 追踪日志TTL（结束时间+1天）
        args[8] = String.valueOf(computeTtlSeconds(seckillVoucherFullModel));
        // 返回参数数组
        return args;
    }
    /**
     * 计算追踪日志的TTL（生存时间）
     * 计算公式：当前时间到券结束时间的秒数 + 1天
     * 目的：确保追踪日志在券活动结束后还能保留1天，便于对账
     *
     * @param seckillVoucherFullModel 秒杀券完整信息
     * @return TTL秒数
     */
    private long computeTtlSeconds(final SeckillVoucherFullModel seckillVoucherFullModel) {
        // 计算当前时间到券结束时间的秒数
        long secondsUntilEnd = Duration.between(LocalDateTimeUtil.now(), seckillVoucherFullModel.getEndTime()).getSeconds();
        // 返回max(1, secondsUntilEnd + 1天)，确保TTL至少为1秒
        return Math.max(1L, secondsUntilEnd + Duration.ofDays(1).getSeconds());
    }
}
