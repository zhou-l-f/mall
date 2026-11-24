package com.macro.mall.seckill.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.seckill.domain.SeckillActivity;
import com.macro.mall.seckill.domain.SeckillResult;
import com.macro.mall.seckill.domain.SeckillSku;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀 Redis 操作工具
 * 全部使用 StringRedisTemplate，保证 Lua 脚本读到的是纯数字字符串
 */
@Component
public class SeckillRedisUtil {

    public static final long SOLD_OUT = -1L;
    public static final long LIMIT_EXCEEDED = -2L;
    public static final long SUCCESS = 1L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> seckillScript;

    public SeckillRedisUtil(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.seckillScript = new DefaultRedisScript<>();
        this.seckillScript.setLocation(new ClassPathResource("lua/seckill.lua"));
        this.seckillScript.setResultType(Long.class);
    }

    public String activityKey(Long activityId) {
        return "seckill:activity:{" + activityId + "}";
    }

    public String stockKey(Long activityId, Long skuId) {
        return "seckill:stock:{" + activityId + ":" + skuId + "}";
    }

    public String boughtKey(Long activityId, Long skuId) {
        return "seckill:bought:{" + activityId + ":" + skuId + "}";
    }

    public String skuKey(Long activityId, Long skuId) {
        return "seckill:sku:{" + activityId + ":" + skuId + "}";
    }

    public String resultKey(String orderSn) {
        return "seckill:order:result:" + orderSn;
    }

    public String orderSnIncrKey() {
        return "seckill:orderSn:incr";
    }

    /**
     * 执行抢购脚本：限购校验 -> 扣库存 -> 写防重
     */
    public long executeSeckill(Long activityId, Long skuId, Integer limitCount, Long memberId) {
        return stringRedisTemplate.execute(seckillScript,
                Arrays.asList(stockKey(activityId, skuId), boughtKey(activityId, skuId)),
                String.valueOf(limitCount), String.valueOf(memberId));
    }

    /**
     * 仅当库存 key 不存在时写入（SETNX），避免覆盖进行中活动已扣减的库存
     */
    public void initStockIfAbsent(Long activityId, Long skuId, Integer stock, long expireSeconds) {
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey(activityId, skuId), String.valueOf(stock),
                expireSeconds, TimeUnit.SECONDS);
    }

    public Integer getStock(Long activityId, Long skuId) {
        String value = stringRedisTemplate.opsForValue().get(stockKey(activityId, skuId));
        return value == null ? null : Integer.valueOf(value);
    }

    public Integer getBoughtCount(Long activityId, Long skuId, Long memberId) {
        Object value = stringRedisTemplate.opsForHash().get(boughtKey(activityId, skuId), String.valueOf(memberId));
        return value == null ? 0 : Integer.valueOf(value.toString());
    }

    public void incrStock(Long activityId, Long skuId, int quantity) {
        stringRedisTemplate.opsForValue().increment(stockKey(activityId, skuId), quantity);
    }

    public void decrBought(Long activityId, Long skuId, Long memberId, int quantity) {
        stringRedisTemplate.opsForHash().increment(boughtKey(activityId, skuId), String.valueOf(memberId), -quantity);
    }

    /**
     * 给防重 Hash 设置过期时间，避免活动结束后长期占用内存
     */
    public void expireBought(Long activityId, Long skuId, long expireSeconds) {
        stringRedisTemplate.expire(boughtKey(activityId, skuId), expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 写入活动窗口信息（覆盖写，活动配置变更时由后台删除）
     */
    public void putActivity(SeckillActivity activity, long expireSeconds) {
        try {
            String json = objectMapper.writeValueAsString(activity);
            stringRedisTemplate.opsForValue().set(activityKey(activity.getId()), json,
                    expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("序列化秒杀活动失败", e);
        }
    }

    public SeckillActivity getActivity(Long activityId) {
        String json = stringRedisTemplate.opsForValue().get(activityKey(activityId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillActivity.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteActivity(Long activityId) {
        stringRedisTemplate.delete(activityKey(activityId));
    }

    /**
     * 写入秒杀商品配置（覆盖写，后台变更配置时删除），抢购热路径直接读缓存避免每请求查库
     */
    public void putSku(SeckillSku sku, long expireSeconds) {
        try {
            String json = objectMapper.writeValueAsString(sku);
            stringRedisTemplate.opsForValue().set(skuKey(sku.getActivityId(), sku.getSkuId()), json,
                    expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("序列化秒杀商品失败", e);
        }
    }

    public SeckillSku getSku(Long activityId, Long skuId) {
        String json = stringRedisTemplate.opsForValue().get(skuKey(activityId, skuId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillSku.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteSku(Long activityId, Long skuId) {
        stringRedisTemplate.delete(skuKey(activityId, skuId));
    }

    /**
     * 配置变更后清理预热 key，库存 key 删除后由预热任务或首次访问重建
     */
    public void deleteStockAndBought(Long activityId, Long skuId) {
        stringRedisTemplate.delete(Arrays.asList(stockKey(activityId, skuId), boughtKey(activityId, skuId)));
    }

    public void setResult(String orderSn, String value, long expireSeconds) {
        stringRedisTemplate.opsForValue().set(resultKey(orderSn), value, expireSeconds, TimeUnit.SECONDS);
    }

    public String getResult(String orderSn) {
        return stringRedisTemplate.opsForValue().get(resultKey(orderSn));
    }

    /**
     * 以 JSON 形式写入抢购结果，便于查询接口直接反序列化
     */
    public void setResultObject(SeckillResult result, long expireSeconds) {
        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(resultKey(result.getOrderSn()), json,
                    expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("序列化秒杀结果失败", e);
        }
    }

    public SeckillResult getResultObject(String orderSn) {
        String json = getResult(orderSn);
        if (json == null || !json.startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    public long incrementRetry(String orderSn) {
        Long value = stringRedisTemplate.opsForValue().increment("seckill:retry:" + orderSn);
        stringRedisTemplate.expire("seckill:retry:" + orderSn, 1, TimeUnit.HOURS);
        return value == null ? 0 : value;
    }

    public void deleteRetry(String orderSn) {
        stringRedisTemplate.delete("seckill:retry:" + orderSn);
    }

    /**
     * 发布失败补偿标记，保证同一条消息只补偿一次
     *
     * @return true 表示首次补偿
     */
    public boolean markCompensated(String orderSn) {
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent("seckill:compensated:" + orderSn, "1", 1, TimeUnit.HOURS);
        return Boolean.TRUE.equals(first);
    }
}
