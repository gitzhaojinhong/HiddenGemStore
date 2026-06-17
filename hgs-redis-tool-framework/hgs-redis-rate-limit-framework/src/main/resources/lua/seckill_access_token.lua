-- 校验并消费访问令牌：匹配则删除，返回1；否则返回0
local key = KEYS[1]
local expected = ARGV[1]
local val = redis.call('get', key)
if val == expected then
    return redis.call('del', key)
else
    return 0
end