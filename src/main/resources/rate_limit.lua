-- KEYS[1] = rate:<module>:<IP>:<yyyyMMddHHmm>
-- KEYS[2] = blacklist:<module>:<IP>
-- KEYS[3] = violation:user:<userId>
-- ARGV[1] = 限流阈值
-- ARGV[2] = 封号阈值
-- ARGV[3] = 分钟窗口 TTL
-- ARGV[4] = 封禁 TTL

-- 返回: {status, 当前分钟计数, 当前违规计数}
--   status: 0=正常放行  1=封禁中(静默)  2=触发封禁+违规  3=触发封号

local banned = redis.call('GET', KEYS[2])
if banned then
    local v = redis.call('GET', KEYS[3])
    return {1, 0, tonumber(v) or 0}
end

local current = redis.call('GET', KEYS[1])

if current then
    local c = tonumber(current)

    if c >= tonumber(ARGV[1]) then
        redis.call('SETEX', KEYS[2], ARGV[4], '1')

        local v = redis.call('INCR', KEYS[3])
        if v == 1 then
            redis.call('EXPIRE', KEYS[3], 86400)
        end

        if v >= tonumber(ARGV[2]) then
            return {3, c + 1, v}
        end
        return {2, c + 1, v}
    end

    local newCount = redis.call('INCR', KEYS[1])
    local v = redis.call('GET', KEYS[3])
    return {0, newCount, tonumber(v) or 0}
else
    redis.call('INCR', KEYS[1])
    redis.call('EXPIRE', KEYS[1], ARGV[3])
    local v = redis.call('GET', KEYS[3])
    return {0, 1, tonumber(v) or 0}
end
