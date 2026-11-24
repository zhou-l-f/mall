package com.macro.mall.seckill.component;

import com.macro.mall.seckill.dao.SeckillDao;
import com.macro.mall.seckill.domain.SeckillActivity;
import com.macro.mall.seckill.domain.SeckillSku;
import com.macro.mall.seckill.util.SeckillRedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 秒杀活动预热任务
 * 将活动窗口与秒杀库存写入 Redis；重复预热使用 SETNX，避免覆盖进行中活动已扣减的库存
 */
@Component
public class SeckillPreheatTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillPreheatTask.class);

    /**
     * 活动结束后 key 额外保留一天，便于对账与结果查询
     */
    private static final long AFTER_END_EXTRA_SECONDS = 86400L;

    private final SeckillDao seckillDao;
    private final SeckillRedisUtil redisUtil;

    @Value("${seckill.preheat.ahead-minutes:5}")
    private int aheadMinutes;
    @Value("${seckill.preheat.batch-size:50}")
    private int batchSize;

    public SeckillPreheatTask(SeckillDao seckillDao, SeckillRedisUtil redisUtil) {
        this.seckillDao = seckillDao;
        this.redisUtil = redisUtil;
    }

    @Scheduled(fixedDelayString = "${seckill.preheat.interval-ms:10000}", initialDelay = 5000)
    public void preheat() {
        try {
            doPreheat();
        } catch (Exception e) {
            LOGGER.error("秒杀活动预热失败", e);
        }
    }

    private void doPreheat() {
        Date now = new Date();
        List<SeckillActivity> activities = seckillDao.selectActivitiesForPreheat(now, aheadMinutes, batchSize);
        if (activities == null || activities.isEmpty()) {
            return;
        }
        for (SeckillActivity activity : activities) {
            long expireSeconds = calculateExpireSeconds(activity, now);
            redisUtil.putActivity(activity, expireSeconds);
            List<SeckillSku> skus = seckillDao.selectSkusByActivityId(activity.getId());
            if (skus == null) {
                continue;
            }
            for (SeckillSku sku : skus) {
                if (sku.getStatus() != null && sku.getStatus() == 1) {
                    redisUtil.initStockIfAbsent(activity.getId(), sku.getSkuId(), sku.getSeckillStock(), expireSeconds);
                }
            }
            LOGGER.debug("秒杀活动预热完成, activityId={}, skuCount={}", activity.getId(), skus.size());
        }
    }

    private long calculateExpireSeconds(SeckillActivity activity, Date now) {
        long endMillis = activity.getEndTime() == null ? now.getTime() : activity.getEndTime().getTime();
        long seconds = (endMillis - now.getTime()) / 1000 + AFTER_END_EXTRA_SECONDS;
        return Math.max(seconds, 60L);
    }
}
