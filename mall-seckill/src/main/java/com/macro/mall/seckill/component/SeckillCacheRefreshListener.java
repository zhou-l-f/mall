package com.macro.mall.seckill.component;

import com.macro.mall.seckill.util.SeckillRedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 监听后台配置变更广播，主动失效本地缓存
 * 消息格式：A:{activityId} 活动变更；S:{activityId}:{skuId} 商品变更；ALL 全量失效
 */
@Component
public class SeckillCacheRefreshListener implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillCacheRefreshListener.class);

    private final SeckillLocalCache localCache;

    public SeckillCacheRefreshListener(SeckillLocalCache localCache) {
        this.localCache = localCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if ("ALL".equals(body)) {
                localCache.invalidateAll();
                return;
            }
            String[] parts = body.split(":");
            if (parts.length >= 2 && "A".equals(parts[0])) {
                localCache.invalidateActivity(Long.valueOf(parts[1]));
            } else if (parts.length >= 3 && "S".equals(parts[0])) {
                localCache.invalidateSku(Long.valueOf(parts[1]), Long.valueOf(parts[2]));
            }
        } catch (Exception e) {
            LOGGER.warn("处理秒杀配置变更广播失败, channel={}, message={}",
                    SeckillRedisUtil.CONFIG_REFRESH_CHANNEL, body, e);
        }
    }
}
