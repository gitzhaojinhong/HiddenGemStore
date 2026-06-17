-- 令牌桶限流（支持按 IP 与用户两个维度）
-- 设计说明：
--   - 容量 capacity = maxAttempts（突发能力由容量决定）
--   - 生成速率 ratePerMs = maxAttempts / windowMillis（平均速率）
--   - 使用 Redis TIME 作为统一时钟，避免应用与Redis时钟漂移
--   - TTL = windowMillis*2 + 随机抖动(window/10)，缓解集中过期抖动
--   - 判定顺序：先按IP维度，再按用户维度（用户维度更细粒度）
-- IP维度桶（HASH），可选
local ipKey = KEYS[1]
-- 用户维度桶（HASH），必选
local userKey = KEYS[2]
-- P窗口毫秒数（用于推导速率与TTL）
local ipWindowMillis = tonumber(ARGV[1] or '0')
-- IP最大尝试次数（映射为桶容量）
local ipMaxAttempts = tonumber(ARGV[2] or '0')
-- 用户窗口毫秒数（用于推导速率与TTL）
local userWindowMillis = tonumber(ARGV[3] or '0')
-- 用户最大尝试次数（映射为桶容量）
local userMaxAttempts = tonumber(ARGV[4] or '0')

-- 返回码与 Java BaseCode 保持一致
local CODE_SUCCESS = 0         -- 允许
local CODE_IP_EXCEEDED = 10007 -- IP 维度限流（超限）
local CODE_USER_EXCEEDED = 10008 -- 用户维度限流（超限）

-- 当前毫秒时间：TIME 返回 [seconds, microseconds]
local now = redis.call('TIME')
-- 统一时间基于Redis服务器，转化为毫秒
local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)

-- 函数：限制补充间隔上限。安全钳制Δt的上限，避免一次性过量补充；默认设置上限为窗口的2倍
-- @param delta 距离上次更新令牌桶的时间间隔
-- @param window 限流的时间窗口
local function clampDelta(delta, window)
    -- 时钟回退保护：不补充
    if delta < 0 then
        return 0
    end
    -- 上限：窗口的2倍
    local maxDelta = window > 0 and window * 2 or 0
    if maxDelta > 0 and delta > maxDelta then
        return maxDelta
    end
    return delta
end
-- 函数：计算并消费令牌
-- @param bucketKey 令牌桶的KEY
-- @param windowMillis 窗口毫秒数
-- @param maxAttempts 最大尝试次数
local function tryConsume(bucketKey,windowMillis,maxAttempts)
    -- 返回 true 表示允许；返回 false 表示拒绝
    if bucketKey == nil or bucketKey == '' or windowMillis <= 0 or maxAttempts <= 0 then
        return true  -- 该维度未启用或配置非法：直接通过
    end
    -- 桶容量（突发上限）
    local capacity = maxAttempts
    -- 平均令牌生成速率（每毫秒）
    local ratePerMs = maxAttempts/windowMillis

    -- 获取令牌桶信息
    local lastMs = tonumber(redis.call('HGET', bucketKey, 'last_ms'))
    -- 浮点数（Double），可能出现极小误差
    local tokens = tonumber(redis.call('HGET', bucketKey, 'tokens'))

    -- 如果桶不存在，则初始化
    if not lastMs then
        -- 冷启动：初始化为满桶，能支撑首波合理突发
        lastMs = nowMillis
        tokens = capacity
    end
    -- 计算距离上次更新的时间间隔
    local delta = clampDelta(nowMillis - lastMs, windowMillis)
    -- 本次可补充的令牌数
    local refill = delta * ratePerMs
    -- 桶中令牌不能超过容量
    tokens = math.min(capacity, tokens + refill)

    if tokens >= 1.0 then
        -- 消费一个令牌
        tokens = tokens - 1.0
        -- 更新桶状态（令牌数与最后更新时间）
        redis.call('HSET', bucketKey, 'tokens', tokens)
        redis.call('HSET', bucketKey, 'last_ms', nowMillis)
        -- TTL：窗口*2 + 随机抖动（窗口/10），降低集中过期导致负载尖峰
        local ttl = (windowMillis * 2) + math.random(0, math.max(1, math.floor(windowMillis / 10)))
        redis.call('PEXPIRE', bucketKey, ttl)
        return true
    else
        -- 令牌不足：仍更新 last_ms 与 tokens，保证后续能按速率继续补充
        redis.call('HSET', bucketKey, 'tokens', tokens)
        redis.call('HSET', bucketKey, 'last_ms', nowMillis)
        local ttl = (windowMillis * 2) + math.random(0, math.max(1, math.floor(windowMillis / 10)))
        redis.call('PEXPIRE', bucketKey, ttl)
        return false
    end
end
-- 先按IP维度判定（若启用）
local ipAllowed = tryConsume(ipKey, ipWindowMillis, ipMaxAttempts)
if not ipAllowed then
    return CODE_IP_EXCEEDED
end

-- 再按用户维度判定（必选）
local userAllowed = tryConsume(userKey, userWindowMillis, userMaxAttempts)
if not userAllowed then
    return CODE_USER_EXCEEDED
end
-- 两个维度均允许
return CODE_SUCCESS