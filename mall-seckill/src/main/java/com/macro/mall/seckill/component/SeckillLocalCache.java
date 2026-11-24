package com.macro.mall.seckill.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.macro.mall.seckill.domain.SeckillActivity;
import com.macro.mall.seckill.domain.SeckillSku;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 活动与商品配置的进程内缓存（Caffeine，默认 60 秒过期）
 * 后台修改配置时发布 Redis 广播消息主动失效，避免最长 60 秒的脏读
 */
@Component
public class SeckillLocalCache {

    private final Cache<Long, SeckillActivity> activityCache;
    private final Cache<String, SeckillSku> skuCache;

    public SeckillLocalCache(@Value("${seckill.local-cache-seconds:60}") long cacheSeconds) {
        this.activityCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheSeconds, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
        this.skuCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheSeconds, TimeUnit.SECONDS)
                .maximumSize(10000)
                .build();
    }

    public SeckillActivity getActivity(Long activityId) {
        return activityCache.getIfPresent(activityId);
    }

    public void putActivity(SeckillActivity activity) {
        activityCache.put(activity.getId(), activity);
    }

    public void invalidateActivity(Long activityId) {
        activityCache.invalidate(activityId);
    }

    public SeckillSku getSku(Long activityId, Long skuId) {
        return skuCache.getIfPresent(skuKey(activityId, skuId));
    }

    public void putSku(SeckillSku sku) {
        skuCache.put(skuKey(sku.getActivityId(), sku.getSkuId()), sku);
    }

    public void invalidateSku(Long activityId, Long skuId) {
        skuCache.invalidate(skuKey(activityId, skuId));
    }

    public void invalidateAll() {
        activityCache.invalidateAll();
        skuCache.invalidateAll();
    }

    private String skuKey(Long activityId, Long skuId) {
        return activityId + ":" + skuId;
    }
}
