package com.macro.mall.seckill.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.seckill.domain.SeckillActivity;
import com.macro.mall.seckill.domain.SeckillResult;
import com.macro.mall.seckill.domain.SeckillSku;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀 Redis 操作工具
 * 全部使用 StringRedisTemplate，保证 Lua 脚本读到的是纯数字字符串
 */
@Component
public class SeckillRedisUtil {

    public static final long SOLD_OUT = -1L;
    public static final long LIMIT_EXCEEDED = -2L;
    public static final long NOT_STARTED = -3L;
    public static final long ACTIVITY_ENDED = -4L;
    public static final long SUCCESS = 1L;
    public static final long SCRIPT_ERROR = -99L;

    /**
     * 后台配置变更广播频道，抢购服务订阅后失效本地缓存
     */
    public static final String CONFIG_REFRESH_CHANNEL = "seckill:config:refresh";

    /**
     * 请求标记保留时长：只需覆盖客户端超时判定窗口
     */
    private static final long REQUEST_MARKER_SECONDS = 30L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisScript<String> seckillScript;

    public SeckillRedisUtil(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.seckillScript = new LockFreeRedisScript(loadScriptText("lua/seckill.lua"));
    }

    /**
     * 抢购脚本返回结果
     *
     * @param code    结果码，SUCCESS 时 orderSn 有值
     * @param orderSn 生成的订单号
     */
    public record SeckillScriptResult(long code, String orderSn) {
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
        return resultKeyPrefix() + orderSn;
    }

    public String resultKeyPrefix() {
        return "seckill:order:result:";
    }

    public String orderSnIncrKey() {
        return "seckill:orderSn:incr";
    }

    public String requestMarkerPrefix() {
        return "seckill:req:";
    }

    /**
     * 读取请求标记：脚本执行成功时写入订单号，返回 null 表示扣减未发生
     */
    public String getScriptOrderSn(String requestId) {
        return stringRedisTemplate.opsForValue().get(requestMarkerPrefix() + requestId);
    }

    /**
     * 一次往返完成：活动窗口校验、库存预检、限购校验、扣减、订单号生成、结果写入
     *
     * @return 成功时返回订单号，失败时返回对应结果码
     */
    public SeckillScriptResult executeSeckill(Long activityId, Long skuId, Long memberId, int limitCount,
                                              Integer initialStock, long activityExpireSeconds,
                                              long resultExpireSeconds, long nowMillis,
                                              long startMillis, long endMillis, String orderSnPrefix,
                                              String requestId) {
        String result = stringRedisTemplate.execute(seckillScript,
                Arrays.asList(stockKey(activityId, skuId), boughtKey(activityId, skuId), orderSnIncrKey()),
                String.valueOf(limitCount), String.valueOf(memberId), String.valueOf(initialStock),
                String.valueOf(activityExpireSeconds), String.valueOf(resultExpireSeconds),
                String.valueOf(nowMillis), String.valueOf(startMillis), String.valueOf(endMillis),
                orderSnPrefix, resultKeyPrefix(), requestId, requestMarkerPrefix(),
                String.valueOf(REQUEST_MARKER_SECONDS));
        if (result == null) {
            return new SeckillScriptResult(SCRIPT_ERROR, null);
        }
        if (result.startsWith("1|")) {
            return new SeckillScriptResult(SUCCESS, result.substring(2));
        }
        return new SeckillScriptResult(Long.parseLong(result), null);
    }

    /**
     * 仅当库存 key 不存在时写入（SETNX），避免覆盖进行中活动已扣减的库存
     */
    public void initStockIfAbsent(Long activityId, Long skuId, Integer stock, long expireSeconds) {
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey(activityId, skuId), String.valueOf(stock),
                expireSeconds, TimeUnit.SECONDS);
    }

    public Integer getBoughtCount(Long activityId, Long skuId, Long memberId) {
        Object value = stringRedisTemplate.opsForHash().get(boughtKey(activityId, skuId), String.valueOf(memberId));
        return value == null ? 0 : Integer.valueOf(value.toString());
    }

    public void incrStock(Long activityId, Long skuId, int quantity) {
        stringRedisTemplate.opsForValue().increment(stockKey(activityId, skuId), quantity);
    }

    /**
     * 回补限购计数，计数归零后直接删除字段，避免补偿后残留 0 值成员
     */
    public void decrBought(Long activityId, Long skuId, Long memberId, int quantity) {
        String key = boughtKey(activityId, skuId);
        String field = String.valueOf(memberId);
        Long value = stringRedisTemplate.opsForHash().increment(key, field, -quantity);
        if (value != null && value <= 0) {
            stringRedisTemplate.opsForHash().delete(key, field);
        }
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

    private static String loadScriptText(String path) {
        ClassLoader classLoader = SeckillRedisUtil.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("秒杀脚本不存在: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取秒杀脚本失败: " + path, e);
        }
    }

    /**
     * 预计算 SHA1 的脚本实现
     * DefaultRedisScript#getSha1() 每次调用都要获取实例级 ReentrantLock（即使 SHA1 已缓存），
     * 高并发下所有抢购线程在这把锁上串行排队，成为吞吐瓶颈；
     * 这里在构造期一次性算出 SHA1，执行期完全无锁
     */
    private static final class LockFreeRedisScript implements RedisScript<String> {

        private final String script;
        private final String sha1;

        private LockFreeRedisScript(String script) {
            this.script = script;
            this.sha1 = sha1Hex(script);
        }

        @Override
        public String getSha1() {
            return sha1;
        }

        @Override
        public Class<String> getResultType() {
            return String.class;
        }

        @Override
        public String getScriptAsString() {
            return script;
        }

        private static String sha1Hex(String text) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("无法计算秒杀脚本 SHA1", e);
            }
        }
    }
}
