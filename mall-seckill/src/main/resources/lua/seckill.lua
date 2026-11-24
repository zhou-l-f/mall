-- KEYS[1] = 库存 key（hash tag: {activityId:skuId}）
-- KEYS[2] = 防重 Hash key（同 hash tag）
-- ARGV[1] = 每人限购数
-- ARGV[2] = 会员标识
local stock = tonumber(redis.call('get', KEYS[1]) or '0')
if stock <= 0 then
    return -1                       -- 已售罄
end
local bought = tonumber(redis.call('hget', KEYS[2], ARGV[2]) or '0')
if bought >= tonumber(ARGV[1]) then
    return -2                       -- 超过限购
end
redis.call('decr', KEYS[1])
redis.call('hincrby', KEYS[2], ARGV[2], 1)
return 1                            -- 抢购成功
