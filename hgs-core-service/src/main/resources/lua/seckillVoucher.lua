-- 1.参数列表
-- 单槽位库存key（已格式化，带HashTag）
local stockKey = KEYS[1]
-- 单槽位用户集合key（已格式化，带HashTag）
local seckillUserKey = KEYS[2]
-- 单槽位扣减日志key（已格式化，带HashTag）
local traceLogKey = KEYS[3]

-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 活动开始/结束时间（毫秒）
local beginTime = tonumber(ARGV[3])
local endTime = tonumber(ARGV[4])
-- 优惠券的状态
local status = tonumber(ARGV[5])
-- 订单id与日志TTL（秒）
local orderId = ARGV[6]
-- 追踪日志ID
local traceId = ARGV[7]
-- 操作类型
local logType = ARGV[8]
-- 追踪日志的TTL
local ttlSeconds = tonumber(ARGV[9])


-- 2.脚本业务
-- 当前时间
local timeArr = redis.call("TIME")
-- nowMillis将timeArr转换为毫秒
local nowMillis = tonumber(timeArr[1]) * 1000 + math.floor(tonumber(timeArr[2]) / 1000)

-- 时间范围判断
if nowMillis < beginTime then
    return string.format('{"%s": %d}', 'code', 10002)
end
if nowMillis > endTime then
    return string.format('{"%s": %d}', 'code', 10003)
end
-- 判断优惠卷状态
if (status == 2) then
    return string.format('{"%s": %d}', 'code', 10011)
end
if (status == 3) then
    return string.format('{"%s": %d}', 'code', 10012)
end
local stock = redis.call("get",stockKey)
-- 缓存中的秒杀券库存为空，则直接返回
if not stock then
    return string.format('{"%s": %d}', 'code', 10004)
end
-- 判断库存是否充足
if tonumber(stock) <= 0 then
    -- 库存不足
    return string.format('{"%s": %d}', 'code', 10005)
end
-- 判断用户是否下过单
if (redis.call('sismember',seckillUserKey,userId) == 1) then
    -- 用户已下过单
    return string.format('{"%s": %d}', 'code', 10006)
end
-- 扣减库存
-- 记录扣减前数量与扣减量/扣减后数量
local beforeQty = tonumber(stock)
local changeQty = 1
local afterQty = beforeQty - changeQty
-- 下单
redis.call('incrby',stockKey,-changeQty)
-- 记录扣减日志
local timeArr2 = redis.call('TIME')
-- 时间戳
local logNowMillis = tonumber(timeArr2[1]) * 1000 + math.floor(tonumber(timeArr2[2]) / 1000)
-- 扣减日志的信息
local logEntry = cjson.encode({
    logType = logType, -- 操作类型
    ts = logNowMillis, -- 时间戳
    orderId = orderId, -- 订单id
    traceId = traceId, -- 追踪日志ID
    userId = userId,   -- 用户id
    voucherId = voucherId, -- 优惠券id
    beforeQty = beforeQty, -- 扣减前数量
    changeQty = changeQty, -- 扣减量
    afterQty = afterQty    -- 扣减后数量
})
-- 设置扣减信息，使用hash类型，key：链路id，方便和数据库中的记录关联。value：信息
redis.call('hset',traceLogKey,traceId,logEntry)
if ttlSeconds and ttlSeconds>0 then
    -- 设置TTL过期时间
    redis.call('expire',traceLogKey,ttlSeconds)
end
-- 返回信息，包括code码、扣减前数量，扣减数量，扣减后数量
return string.format('{"%s": %d, "%s": %s, "%s": %s, "%s": %s}', 'code', 0, 'beforeQty', beforeQty, 'deductQty', changeQty, 'afterQty', afterQty)
