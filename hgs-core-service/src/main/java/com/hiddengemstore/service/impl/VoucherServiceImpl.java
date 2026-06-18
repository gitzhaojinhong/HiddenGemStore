package com.hiddengemstore.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiddengemstore.cache.SeckillVoucherCacheInvalidationPublisher;

import com.hiddengemstore.core.SnowflakeIdGenerator;
import com.hiddengemstore.entity.SeckillVoucher;
import com.hiddengemstore.entity.Voucher;
import com.hiddengemstore.entity.dto.*;
import com.hiddengemstore.entity.vo.GetSubscribeStatusVo;
import com.hiddengemstore.enums.BaseCode;
import com.hiddengemstore.enums.RedisKeyManage;
import com.hiddengemstore.enums.StockUpdateType;
import com.hiddengemstore.enums.SubscribeStatus;
import com.hiddengemstore.exception.HGSFrameException;
import com.hiddengemstore.factory.BloomFilterHandlerFactory;
import com.hiddengemstore.lock.LockType;
import com.hiddengemstore.lock.annotation.ServiceLock;
import com.hiddengemstore.mapper.SeckillVoucherMapper;
import com.hiddengemstore.mapper.VoucherMapper;
import com.hiddengemstore.redis.api.RedisCache;
import com.hiddengemstore.redis.internal.RedisKeyBuild;
import com.hiddengemstore.service.IVoucherOrderService;
import com.hiddengemstore.service.IVoucherService;
import com.hiddengemstore.uitls.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hiddengemstore.constant.Constant.BLOOM_FILTER_HANDLER_VOUCHER;
import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_LOCK;
import static com.hiddengemstore.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_STOCK_LOCK;
import static com.hiddengemstore.service.impl.VoucherOrderServiceImpl.SECKILL_ORDER_EXECUTOR;

@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private SeckillVoucherCacheInvalidationPublisher seckillVoucherCacheInvalidationPublisher;
    @Resource
    private RedisCache redisCache;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;


    @Override
    public Long addVoucher(VoucherDto voucherDto) {
        Voucher one = lambdaQuery().orderByDesc(Voucher::getId).one(); // 查询当前最大ID的券记录
        long newId = 1L;                                               // 默认从1开始
        if (one != null) {
            newId = one.getId() + 1;                                   // 在最大ID基础上+1，避免主键冲突
        }
        Voucher voucher = new Voucher();                               // 创建券实体
        BeanUtil.copyProperties(voucherDto, voucher);                  // DTO属性拷贝到实体
        voucher.setId(newId);                                        // 设置自增ID
        save(voucher);                                                 // 持久化到数据库
        bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_VOUCHER).add(voucher.getId().toString()); // 将券ID加入布隆过滤器，加速后续秒杀时的存在性校验
        return voucher.getId();                                        // 返回新券ID
    }

    @Override
    @ServiceLock(lockType = LockType.Write, name = UPDATE_SECKILL_VOUCHER_LOCK, keys = "#updateSeckillVoucherDto.voucherId")
    @Transactional(rollbackFor = Exception.class)// 开启事务 rollbackFor = Exception.class表示出现异常时回滚
    public void updateSeckillVoucher(UpdateSeckillVoucherDto updateSeckillVoucherDto) {
        Long voucherId = updateSeckillVoucherDto.getVoucherId();
        // 更新 tb_voucher 表的非空字段
        boolean voucherUpdatedStatus = false;
        LambdaUpdateChainWrapper<Voucher> voucherWrapper = this.lambdaUpdate().eq(Voucher::getId, voucherId);
        if (updateSeckillVoucherDto.getTitle() != null) {
            voucherWrapper.set(Voucher::getTitle, updateSeckillVoucherDto.getTitle());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getSubTitle() != null) {
            voucherWrapper.set(Voucher::getSubTitle, updateSeckillVoucherDto.getSubTitle());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getRules() != null) {
            voucherWrapper.set(Voucher::getRules, updateSeckillVoucherDto.getRules());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getPayValue() != null) {
            voucherWrapper.set(Voucher::getPayValue, updateSeckillVoucherDto.getPayValue());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getActualValue() != null) {
            voucherWrapper.set(Voucher::getActualValue, updateSeckillVoucherDto.getActualValue());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getType() != null) {
            voucherWrapper.set(Voucher::getType, updateSeckillVoucherDto.getType());
            voucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getStatus() != null) {
            voucherWrapper.set(Voucher::getStatus, updateSeckillVoucherDto.getStatus());
            voucherUpdatedStatus = true;
        }
        if (voucherUpdatedStatus) {
            voucherWrapper.set(Voucher::getUpdateTime, LocalDateTimeUtil.now());
        }
        // 更新 tb_seckill_voucher 表的非空字段（仅时间相关）
        boolean seckillVoucherUpdatedStatus = false;
        LambdaUpdateWrapper<SeckillVoucher> seckillWrapper = new LambdaUpdateWrapper<>();
        seckillWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        if (updateSeckillVoucherDto.getBeginTime() != null) {
            seckillWrapper.set(SeckillVoucher::getBeginTime, updateSeckillVoucherDto.getBeginTime());
            seckillVoucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getEndTime() != null) {
            seckillWrapper.set(SeckillVoucher::getEndTime, updateSeckillVoucherDto.getEndTime());
            seckillVoucherUpdatedStatus = true;
        }

        // 受众规则字段更新
        if (updateSeckillVoucherDto.getAllowedLevels() != null) {
            seckillWrapper.set(SeckillVoucher::getAllowedLevels, updateSeckillVoucherDto.getAllowedLevels());
            seckillVoucherUpdatedStatus = true;
        }
        if (updateSeckillVoucherDto.getMinLevel() != null) {
            seckillWrapper.set(SeckillVoucher::getMinLevel, updateSeckillVoucherDto.getMinLevel());
            seckillVoucherUpdatedStatus = true;
        }
        if (seckillVoucherUpdatedStatus) {
            seckillWrapper.set(SeckillVoucher::getUpdateTime, LocalDateTimeUtil.now());
        }
        // 更新后清理缓存，等待读路径按新数据重建缓存
        if (voucherUpdatedStatus || seckillVoucherUpdatedStatus) {
            voucherWrapper.update();
            seckillVoucherMapper.update(seckillWrapper);
            seckillVoucherCacheInvalidationPublisher.publishInvalidate(voucherId, "update");
        }
    }

    @Override
    public void subscribe(VoucherSubscribeDto voucherSubscribeDto) {
        Long voucherId = voucherSubscribeDto.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);
        // 获取优惠卷的剩余过期时间
        Long ttlSeconds = redisCache.getExpire(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId), TimeUnit.SECONDS);
        if (Objects.isNull(ttlSeconds) || ttlSeconds <= 0) {
            SeckillVoucher seckillVoucher = seckillVoucherMapper.selectOne(new LambdaQueryWrapper<SeckillVoucher>().eq(SeckillVoucher::getVoucherId, voucherId));
            if (Objects.nonNull(seckillVoucher) && Objects.nonNull(seckillVoucher.getEndTime())) {
                ttlSeconds = Math.max(
                        LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).getSeconds(),
                        1L
                );
            }
        } else {
            ttlSeconds = 3600L;
        }
        //判断用户是否已购
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId),
                userIdStr
        ));
        // 如果已购，修改订阅状态并结束
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
        if (purchased) {
            // 修改订阅状态,并为特定字段设置过期时间
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
            // 为订阅队列设置过期时间
            redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
            return;
        }
        // 加入订阅集合（SET），幂等
        RedisKeyBuild setKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId);
        // 返回结果1，表示首次添加
        Long added = redisCache.addForSet(setKey, userIdStr);
        redisCache.expire(setKey, ttlSeconds, TimeUnit.SECONDS);

        //加入订阅队列（ZSET），仅首次加入写入分数,分数为当前时间戳
        RedisKeyBuild zSetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, voucherId);
        if (Objects.nonNull(added) && added > 0) {
            redisCache.addForSortedSet(zSetKey, userIdStr, (double) System.currentTimeMillis(), ttlSeconds, TimeUnit.SECONDS);
        } else {
            // 如果已存在，则对齐订阅队列的过期时间
            redisCache.expire(zSetKey, ttlSeconds, TimeUnit.SECONDS);
        }

        // 更新订阅状态为SUBSCRIBED(已订阅)，如果原始字段为SUCCESS（已购买）则不更新
        Integer prev = redisCache.getForHash(statusKey, userIdStr, Integer.class);
        if (!SubscribeStatus.SUCCESS.getCode().equals(prev)) {
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUBSCRIBED.getCode(), ttlSeconds, TimeUnit.SECONDS);
        }
        // 对齐订阅状态的过期时间
        redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void unsubscribe(VoucherSubscribeDto voucherSubscribeDto) {
        Long voucherId = voucherSubscribeDto.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);

        RedisKeyBuild setKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId);
        RedisKeyBuild zSetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, voucherId);
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);

        // 从订阅集合与队列移除
        redisCache.removeForSet(setKey, userIdStr);
        redisCache.delForSortedSet(zSetKey, userIdStr);

        // 已购则维持 SUCCESS，否则置为 UNSUBSCRIBED
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId),
                userIdStr
        ));
        Long ttlSeconds = redisCache.getExpire(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                TimeUnit.SECONDS
        );
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = 3600L;
        }
        redisCache.putHash(
                statusKey,
                userIdStr,
                purchased ? SubscribeStatus.SUCCESS.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode(),
                ttlSeconds, TimeUnit.SECONDS);
        redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Integer getSubscribeStatus(final VoucherSubscribeDto voucherSubscribeDto) {
        Long voucherId = voucherSubscribeDto.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);

        // 构建订阅状态缓存Key，从Hash结构中获取用户对该券的订阅状态
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
        Integer st = redisCache.getForHash(statusKey, userIdStr, Integer.class);
        // 如果缓存中存在状态，直接返回（缓存命中）
        if (st != null) {
            return st;
        }

        // 缓存未命中，检查用户是否已购买该秒杀券
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId),
                userIdStr
        ));
        // 如果用户已购买，设置状态为SUCCESS并写入缓存
        if (purchased) {
            // 获取秒杀券缓存的剩余过期时间，用于设置状态缓存的TTL
            Long ttlSeconds = redisCache.getExpire(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                    TimeUnit.SECONDS
            );
            // 如果券缓存不存在或已过期，默认设置为1小时
            if (ttlSeconds == null || ttlSeconds <= 0) {
                ttlSeconds = 3600L;
            }
            // 将购买成功状态写入Hash缓存，并设置TTL
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
            // 为整个状态Hash设置过期时间，防止内存泄漏
            redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
            return SubscribeStatus.SUCCESS.getCode();
        }

        // 用户未购买，检查是否在订阅集合中
        boolean inQueue = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId),
                userIdStr
        ));
        // 在订阅集合中返回SUBSCRIBED(已订阅)，否则返回UNSUBSCRIBED(未订阅)
        return inQueue ? SubscribeStatus.SUBSCRIBED.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode();
    }

    @Override
    public List<GetSubscribeStatusVo> getSubscribeStatusBatch(final VoucherSubscribeBatchDto voucherSubscribeBatchDto) {
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);
        List<GetSubscribeStatusVo> res = new ArrayList<>();
        // 遍历所有券ID，批量查询订阅状态
        for (Long voucherId : voucherSubscribeBatchDto.getVoucherIdList()) {
            // 优先使用HASH缓存获取订阅状态
            RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
            Integer st = redisCache.getForHash(statusKey, userIdStr, Integer.class);
            // 缓存命中，直接添加到结果集并跳过后续逻辑
            if (st != null) {
                res.add(new GetSubscribeStatusVo(voucherId, st));
                continue;
            }
            // 缓存未命中，检查用户是否已购买该秒杀券
            boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId),
                    userIdStr
            ));
            // 如果用户已购买，设置状态为SUCCESS并写入缓存
            if (purchased) {
                // 获取秒杀券缓存的剩余过期时间，用于设置状态缓存的TTL
                Long ttlSeconds = redisCache.getExpire(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                        TimeUnit.SECONDS
                );
                // 如果券缓存不存在或已过期，默认设置为1小时
                if (ttlSeconds == null || ttlSeconds <= 0) {
                    ttlSeconds = 3600L;
                }
                // 将购买成功状态写入Hash缓存，并设置TTL
                redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
                // 为整个状态Hash设置过期时间，防止内存泄漏
                redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
                res.add(new GetSubscribeStatusVo(voucherId, SubscribeStatus.SUCCESS.getCode()));
                continue;
            }
            // 用户未购买，检查是否在订阅集合中
            boolean inQueue = Boolean.TRUE.equals(redisCache.isMemberForSet(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId),
                    userIdStr
            ));
            // 在订阅集合中返回SUBSCRIBED(已订阅)，否则返回UNSUBSCRIBED(未订阅)
            res.add(new GetSubscribeStatusVo(voucherId, inQueue ? SubscribeStatus.SUBSCRIBED.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode()));
        }
        return res;
    }

    @Override
    @ServiceLock(lockType = LockType.Write, name = UPDATE_SECKILL_VOUCHER_STOCK_LOCK, keys = {"#updateSeckillVoucherDto.voucherId"})
    // 写锁，按券ID加锁防止并发修改库存
    @Transactional(rollbackFor = Exception.class) // 开启事务，DB操作失败整体回滚
    public void updateSeckillVoucherStock(UpdateSeckillVoucherStockDto updateSeckillVoucherDto) {
        // 查询当前秒杀券信息
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectOne(new LambdaQueryWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, updateSeckillVoucherDto.getVoucherId()));
        if (Objects.isNull(seckillVoucher)) { // 券不存在则抛异常
            throw new HGSFrameException(BaseCode.SECKILL_VOUCHER_NOT_EXIST);
        }
        Integer oldStock = seckillVoucher.getStock();           // 数据库当前剩余库存
        Integer oldInitStock = seckillVoucher.getInitStock();   // 数据库当前初始库存
        Integer newInitStock = updateSeckillVoucherDto.getInitStock(); // 前端传入的新初始库存
        int changeStock = newInitStock - oldInitStock;          // 计算库存变化量
        if (changeStock == 0) {                                 // 无变化直接返回
            return;
        }
        int newStock = oldStock + changeStock;                  // 新剩余库存 = 旧剩余 + 变化量
        if (newStock < 0) {                                    // 剩余库存不能为负
            throw new HGSFrameException(BaseCode.AFTER_SECKILL_VOUCHER_REMAIN_STOCK_NOT_NEGATIVE_NUMBER);
        }
        StockUpdateType stockUpdateType = StockUpdateType.INCREASE; // 判断库存变动类型
        if (changeStock < 0) {                                  // 变化量为负则为减少库存
            stockUpdateType = StockUpdateType.DECREASE;
        }
        // 更新数据库：库存、初始库存、更新时间
        seckillVoucherMapper.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .set(SeckillVoucher::getStock, newStock)
                .set(SeckillVoucher::getInitStock, newInitStock)
                .set(SeckillVoucher::getUpdateTime, LocalDateTimeUtil.now())
                .eq(SeckillVoucher::getVoucherId, seckillVoucher.getVoucherId()));
        // 删除Redis库存缓存，下次秒杀请求时由 loadVoucherStock 从DB重新加载，避免读写竞态
        // （如果是更新redis缓存，就算加读写锁也无法避免读写竞态，原因：读写锁只保护了 Java 应用层 的并发，但秒杀 Lua 脚本是在 Redis 服务端 原子执行的，完全绕过了 Java 锁机制。）
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY,
                updateSeckillVoucherDto.getVoucherId()));
        // 记录库存修改日志
        log.info("修改库存成功！修改库存类型：{},修改前：数据库初始库存：{},修改后：数据库初始库存：{}",
                stockUpdateType.getMsg(),
                oldInitStock,
                newInitStock
        );
        //如果是增加库存,尝试将资格自动分配给订阅队列中最早的未购用户
        if (stockUpdateType == StockUpdateType.INCREASE) {
            SECKILL_ORDER_EXECUTOR.execute(() -> voucherOrderService
                    .autoIssueVoucherToEarliestSubscriber(seckillVoucher.getVoucherId(), null));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addSeckillVoucher(SeckillVoucherDto seckillVoucherDto) {
        // 复用基础券属性，先创建 tb_voucher 记录
        VoucherDto voucherDto = new VoucherDto();
        BeanUtil.copyProperties(seckillVoucherDto, voucherDto);
        Long voucherId = addVoucher(voucherDto);
        // 构建秒杀券实体，雪花算法生成主键，初始库存=剩余库存
        SeckillVoucher seckillVoucher = new SeckillVoucher()
                .setId(snowflakeIdGenerator.nextId())
                .setVoucherId(voucherId)
                .setInitStock(seckillVoucherDto.getStock())
                .setStock(seckillVoucherDto.getStock())
                .setBeginTime(seckillVoucherDto.getBeginTime())
                .setEndTime(seckillVoucherDto.getEndTime())
                .setAllowedLevels(seckillVoucherDto.getAllowedLevels())
                .setMinLevel(seckillVoucherDto.getMinLevel());
        // 持久化秒杀券到 tb_seckill_voucher
        seckillVoucherMapper.insert(seckillVoucher);
        // 计算缓存TTL：秒杀结束时间 - 当前时间，最少1秒
        long ttlSeconds = Math.max(
                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).getSeconds(),
                1L
        );
        // 将库存写入Redis，供秒杀Lua脚本原子扣减
        redisCache.set(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId),
                String.valueOf(seckillVoucher.getStock()),
                ttlSeconds,
                TimeUnit.SECONDS
        );
        // 将秒杀券详情缓存到Redis（stock置空，避免与库存key冗余）
        seckillVoucher.setStock(null);
        redisCache.set(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                seckillVoucher,
                ttlSeconds,
                TimeUnit.SECONDS
        );
        // TODO发送延迟提醒消息，通知用户秒杀即将开始
//        sendDelayedVoucherReminder(seckillVoucher);
        return voucherId;
    }

/*
    public void sendDelayedVoucherReminder(SeckillVoucher seckillVoucher){
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime == null) {
            log.warn("[DELAY_REMINDER] beginTime为空，跳过调度 voucherId={}", seckillVoucher.getVoucherId());
            return;
        }
        long secondsUntilBegin = Math.max(
                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), beginTime).getSeconds(),
                0L
        );
        long delaySeconds = secondsUntilBegin - Math.max(reminderAheadSeconds, 0L);
        if (delaySeconds <= 0) {
            log.info("[DELAY_REMINDER] beginTime过近或已开始，不进行延迟调度 voucherId={} beginTime={} delaySeconds={}",
                    seckillVoucher.getVoucherId(), beginTime, delaySeconds);
            return;
        }

        DelayedVoucherReminderMessage msg = new DelayedVoucherReminderMessage(
                seckillVoucher.getVoucherId(),
                beginTime
        );
        String content = JSON.toJSONString(msg);

        String topic = SpringUtil.getPrefixDistinctionName() + "-" + DELAY_VOUCHER_REMINDER;
        delayQueueContext.sendMessage(topic, content, delaySeconds, TimeUnit.SECONDS);
        log.info("[DELAY_REMINDER] 已调度提醒消息 voucherId={} delaySeconds={} topic={}", seckillVoucher.getVoucherId(), delaySeconds, topic);
    }

 */
}
