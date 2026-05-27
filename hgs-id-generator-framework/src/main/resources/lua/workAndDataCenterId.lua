--- 用于为分布式ID生成器分配唯一的workId和dataCenterId

--- 获取workId和dataCenterId的Redis key
local snowflake_work_id_key = KEYS[1]
local snowflake_data_center_id_key = KEYS[2]

--- 获取workId和dataCenterId的最大值，用于判断是否达到上线
local max_work_id = tonumber(ARGV[1])
local max_data_center_id = tonumber(ARGV[2])

--- 用于返回的workId和dataCenterId
local return_work_id = 0
local return_data_center_id = 0

--- workId和dataCenterId的初始化标志，默认为false，表示redis中不存在
local snowflake_work_id_flag = false
local snowflake_data_center_id_flag = false

--- 判断redis中是否存在workId和dataCenterId,如果没有就执行set初始化,并标记初始化标准为true
if (redis.call('exists', snowflake_work_id_key) == 0) then
    redis.call('set', snowflake_work_id_key, 0)
    snowflake_work_id_flag = true
end
if (redis.call('exists', snowflake_data_center_id_key) == 0) then
    redis.call('set', snowflake_data_center_id_key, 0)
    snowflake_data_center_id_flag = true
end

--- 如果初始化标准都为true,表示workId和dataCenterId都是刚初始化,则直接返回
if (snowflake_work_id_flag and snowflake_data_center_id_flag) then
    return string.format(
            '{"%s": %d, "%s": %d}',
            'workId', return_work_id,
            'dataCenterId', return_data_center_id)
end

--- 获取workId和dataCenterId
local snowflake_work_id = tonumber(redis.call('get', snowflake_work_id_key))
local snowflake_data_center_id = tonumber(redis.call('get', snowflake_data_center_id_key))

--- 处理workId和dataCenterId达到上线的情况
if (snowflake_work_id >= max_work_id) then
    ---归零，循环使用
    redis.call('set', snowflake_work_id_key, 0)
    return_work_id = 0
    if (snowflake_data_center_id >= max_data_center_id) then
        ---归零，循环使用
        redis.call('set', snowflake_data_center_id_key, 0)
        return_data_center_id = 0
    else
        --- dataCenterId自增
        return_data_center_id = redis.call('incr', snowflake_data_center_id_key)
    end
else
    ---- workId自增
    return_work_id = redis.call('incr',snowflake_work_id_key)
end

return string.format(
        '{"%s": %d, "%s": %d}',
        'workId', return_work_id,
        'dataCenterId', return_data_center_id
        )