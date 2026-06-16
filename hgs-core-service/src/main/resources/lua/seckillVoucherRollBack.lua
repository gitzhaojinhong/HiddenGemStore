-- 1.参数列表
-- 单槽位库存key（已格式化，带HashTag）
local stockKey = KEYS[1]
-- 单槽位用户集合key（已格式化，带HashTag）
local seckillUserKey = KEYS[2]
-- 单槽位操作日志key（已格式化，带HashTag）
local traceLogKey = KEYS[3]
-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = (ARGV[2])
-- 订单id
local orderId = ARGV[3]
-- 操作码
local seckillVoucherOrderOperate = tonumber(ARGV[4])
-- 追踪日志ID
local traceId = ARGV[5]
-- 日志类型(恢复)
local logType = ARGV[6]
-- 优惠券库存数量
local beforeQty = tonumber(ARGV[7])
-- 回滚数量
local changeQty = tonumber(ARGV[8])
-- 回滚后库存数量
local afterQty = tonumber(ARGV[9])

-- 2.脚本业务
-- 检查库存键是否存在，不存在返回错误码10004
local stock = redis.call('get' ,stockKey)
if not stock then
    return 10004
end
-- 删除库存键，不修改库存
redis.call('del', stockKey)
-- 如果操作类型为1（删除订单），则从集合中移除用户
if seckillVoucherOrderOperate == 1 then
    if (redis.call('sismember', seckillUserKey, userId) == 1) then
        redis.call('srem', seckillUserKey, userId)
    end
end

-- 获取当前时间戳（毫秒）
local timeArr = redis.call('TIME')
local nowMillis = tonumber(timeArr[1]) * 1000 + math.floor(tonumber(timeArr[2]) / 1000)

-- 构建回滚追踪日志
local logEntry = cjson.encode({
    logType = logType,
    ts = nowMillis,
    orderId = orderId,
    traceId = traceId,
    userId = userId,
    voucherId = voucherId,
    beforeQty = beforeQty,
    changeQty = changeQty,
    afterQty = afterQty
})

-- 保存追踪日志到Hash
redis.call('hset', traceLogKey, traceId, logEntry)

-- 返回成功码0
return 0