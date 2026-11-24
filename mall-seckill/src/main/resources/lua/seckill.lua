-- 合并抢购脚本：活动窗口校验 -> 库存预检 -> 限购校验 -> 扣减 -> 订单号生成 -> 结果写入，单次往返完成
-- KEYS[1] = 库存 key（hash tag: {activityId:skuId}）
-- KEYS[2] = 防重 Hash key（同 hash tag）
-- KEYS[3] = 订单号全局自增 key
-- ARGV[1] = 每人限购数
-- ARGV[2] = 会员标识
-- ARGV[3] = 初始库存（库存 key 不存在时写入）
-- ARGV[4] = 库存/防重 key 过期秒数
-- ARGV[5] = 抢购结果过期秒数
-- ARGV[6] = 当前毫秒时间戳
-- ARGV[7] = 活动开始毫秒（0 表示不限制）
-- ARGV[8] = 活动结束毫秒（0 表示不限制）
-- ARGV[9] = 订单号前缀（SK + 毫秒级时间）
-- ARGV[10] = 抢购结果 key 前缀
local now = tonumber(ARGV[6])
local startMs = tonumber(ARGV[7])
local endMs = tonumber(ARGV[8])
if startMs > 0 and now < startMs then
    return '-3'                     -- 活动未开始
end
if endMs > 0 and now > endMs then
    return '-4'                     -- 活动已结束
end

local stock = redis.call('get', KEYS[1])
if not stock then
    redis.call('set', KEYS[1], ARGV[3], 'EX', tonumber(ARGV[4]))
    stock = ARGV[3]
end
if tonumber(stock) <= 0 then
    return '-1'                     -- 已售罄
end

local bought = tonumber(redis.call('hget', KEYS[2], ARGV[2]) or '0')
if bought >= tonumber(ARGV[1]) then
    return '-2'                     -- 超过限购
end

redis.call('decr', KEYS[1])
redis.call('hincrby', KEYS[2], ARGV[2], 1)
redis.call('expire', KEYS[2], tonumber(ARGV[4]))

local seq = redis.call('incr', KEYS[3])
local orderSn = ARGV[9] .. string.format('%04d', seq % 10000)
redis.call('set', ARGV[10] .. orderSn,
        '{"status":"QUEUING","orderSn":"' .. orderSn .. '"}', 'EX', tonumber(ARGV[5]))
return '1|' .. orderSn
