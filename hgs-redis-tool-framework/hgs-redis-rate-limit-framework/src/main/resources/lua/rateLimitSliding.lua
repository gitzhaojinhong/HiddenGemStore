-- 滑动窗口限流（支持按 IP 与用户两个维度）
-- KEYS[1] = IP维度的ZSET（可选）
local ipKey = KEYS[1]
-- KEYS[2] = 用户维度的ZSET（必选）
local userKey = KEYS[2]
-- ARGV[1] = IP窗口毫秒数
local ipWindowMillis = tonumber(ARGV[1] or '0')
-- ARGV[2] = IP最大尝试次数
local ipMaxAttempts = tonumber(ARGV[2] or '0')
-- ARGV[3] = 用户窗口毫秒数
local userWindowMillis = tonumber(ARGV[3] or '0')
-- ARGV[4] = 用户最大尝试次数
local userMaxAttempts = tonumber(ARGV[4] or '0')

-- 与 Java 中的 BaseCode 保持一致的返回码
local CODE_SUCCESS = 0
local CODE_IP_EXCEEDED = 10007
local CODE_USER_EXCEEDED = 10008

-- 获取当前毫秒时间：TIME 返回 [seconds, microseconds]
local now = redis.call('TIME')
local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)

-- 函数：生成唯一成员值，避免同毫秒内重复
-- @param baseKey 用于构造序列计数器的Key
-- @param ts 毫秒时间戳
local function uniqueMember(baseKey, ts)
    local seqKey = baseKey .. ':seq'
    -- 生成唯一序列号
    local seq = redis.call('INCR', seqKey)
    -- 给序列key设置一个较短过期，避免长时间占用
    if seq == 1 then
        redis.call('PEXPIRE', seqKey, 600000) -- 10分钟
    end
    -- 返回唯一成员值
    return tostring(ts) .. ':' .. tostring(seq)
end
-- 函数：对指定ZSET执行滑动窗口计数，超过则返回指定错误码
-- @param zsetKey 滑动窗口Key
-- @param windowMillis 滑动窗口时间(毫秒)
-- @param maxAttempts 窗口内最大尝试次数
-- @param exceededCode 超限错误码
local function checkSlidingLimit(zsetKey,windowMillis,maxAttempts,exceededCode)
    if zsetKey ~= nil and zsetKey ~= '' and windowMillis > 0 and maxAttempts > 0 then
        -- 添加当前记录
        local member = uniqueMember(zsetKey, nowMillis)
        redis.call('ZADD', zsetKey, nowMillis, member)
        -- 删除窗口外的记录
        redis.call('ZREMRANGEBYSCORE', zsetKey, 0, nowMillis - windowMillis)
        -- 统计当前窗口内的调用次数
        local cnt = redis.call('ZCARD', zsetKey)
        -- 给集合设置一个较短过期，避免长时间占用，并且不影响滑动窗口逻辑
        if cnt == 1 then
            redis.call('PEXPIRE', zsetKey, windowMillis*2)
        end
        if cnt > maxAttempts then
            return exceededCode
        end
    end
    return CODE_SUCCESS
end

-- 先检查IP维度（如果提供了），超过则直接返回
local ipRet = CODE_SUCCESS
if ipKey ~= nil and ipKey ~= '' and ipWindowMillis > 0 and ipMaxAttempts > 0 then
    ipRet = checkSlidingLimit(ipKey, ipWindowMillis, ipMaxAttempts, CODE_IP_EXCEEDED)
    if ipRet ~= CODE_SUCCESS then
        return ipRet
    end
end

-- 再检查用户维度
local userRet = checkSlidingLimit(userKey, userWindowMillis, userMaxAttempts, CODE_USER_EXCEEDED)
if userRet ~= CODE_SUCCESS then
    return userRet
end

return CODE_SUCCESS