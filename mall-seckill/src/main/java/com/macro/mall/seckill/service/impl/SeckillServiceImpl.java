package com.macro.mall.seckill.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.seckill.component.SeckillOrderSender;
import com.macro.mall.seckill.dao.SeckillDao;
import com.macro.mall.seckill.domain.*;
import com.macro.mall.seckill.security.SeckillMemberService;
import com.macro.mall.seckill.service.SeckillService;
import com.macro.mall.seckill.util.SeckillRedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 秒杀抢购Service实现类
 */
@Service
public class SeckillServiceImpl implements SeckillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private static final DateTimeFormatter ORDER_SN_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final long AFTER_END_EXTRA_SECONDS = 86400L;

    private final SeckillDao seckillDao;
    private final SeckillRedisUtil redisUtil;
    private final SeckillMemberService memberService;
    private final SeckillOrderSender orderSender;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${seckill.result-expire-seconds:86400}")
    private long resultExpireSeconds;

    public SeckillServiceImpl(SeckillDao seckillDao, SeckillRedisUtil redisUtil,
                              SeckillMemberService memberService, SeckillOrderSender orderSender,
                              StringRedisTemplate stringRedisTemplate) {
        this.seckillDao = seckillDao;
        this.redisUtil = redisUtil;
        this.memberService = memberService;
        this.orderSender = orderSender;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public SeckillResult seckill(Long activityId, Long skuId) {
        Long memberId = memberService.getCurrentMemberId();
        if (memberId == null) {
            Asserts.fail("请先登录");
        }
        SeckillActivity activity = loadActivity(activityId);
        if (activity == null) {
            Asserts.fail("秒杀活动不存在");
        }
        validateActivityWindow(activity);
        SeckillSku sku = seckillDao.selectSku(activityId, skuId);
        if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
            Asserts.fail("秒杀商品不存在或已下架");
        }
        long expireSeconds = calculateExpireSeconds(activity);
        //冷启动兜底：库存 key 不存在时从 DB 初始化，保证活动开始前未预热也能抢购
        if (redisUtil.getStock(activityId, skuId) == null) {
            redisUtil.initStockIfAbsent(activityId, skuId, sku.getSeckillStock(), expireSeconds);
        }
        int limitCount = sku.getLimitCount() == null ? 1 : sku.getLimitCount();
        long code = redisUtil.executeSeckill(activityId, skuId, limitCount, memberId);
        if (code == SeckillRedisUtil.SOLD_OUT) {
            Asserts.fail("商品已售罄");
        }
        if (code == SeckillRedisUtil.LIMIT_EXCEEDED) {
            Asserts.fail("超出限购数量");
        }
        if (code != SeckillRedisUtil.SUCCESS) {
            Asserts.fail("抢购失败，请稍后重试");
        }
        String orderSn = generateOrderSn();
        redisUtil.setResultObject(SeckillResult.queuing(orderSn), resultExpireSeconds);
        redisUtil.expireBought(activityId, skuId, expireSeconds);

        SeckillOrderMessage message = buildMessage(activity, sku, memberId, orderSn);
        orderSender.send(message);
        LOGGER.info("秒杀抢购成功, orderSn={}, activityId={}, skuId={}, memberId={}",
                orderSn, activityId, skuId, memberId);
        return SeckillResult.queuing(orderSn);
    }

    @Override
    public SeckillActivityDetail getActivityDetail(Long activityId) {
        SeckillActivity activity = loadActivity(activityId);
        if (activity == null) {
            Asserts.fail("秒杀活动不存在");
        }
        List<SeckillSku> skus = seckillDao.selectSkusByActivityId(activityId);
        SeckillActivityDetail detail = new SeckillActivityDetail();
        detail.setActivity(activity);
        detail.setSkus(skus);
        return detail;
    }

    @Override
    public SeckillResult getResult(String orderSn) {
        SeckillResult result = redisUtil.getResultObject(orderSn);
        if (result != null) {
            if (SeckillResult.STATUS_SUCCESS.equals(result.getStatus()) && result.getOrderId() == null) {
                fillOrderInfo(result, orderSn);
            }
            return result;
        }
        //结果 key 过期或丢失时回源订单表
        SeckillResult fromDb = new SeckillResult();
        if (fillOrderInfo(fromDb, orderSn)) {
            return fromDb;
        }
        return SeckillResult.queuing(orderSn);
    }

    /**
     * 用订单表数据补全结果，订单存在返回 true
     */
    private boolean fillOrderInfo(SeckillResult result, String orderSn) {
        com.macro.mall.model.OmsOrder order = seckillDao.selectOrderBySn(orderSn);
        if (order == null) {
            return false;
        }
        result.setStatus(SeckillResult.STATUS_SUCCESS);
        result.setOrderSn(orderSn);
        result.setOrderId(order.getId());
        result.setPayAmount(order.getPayAmount());
        result.setReason(null);
        return true;
    }

    private SeckillOrderMessage buildMessage(SeckillActivity activity, SeckillSku sku,
                                             Long memberId, String orderSn) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setActivityId(activity.getId());
        message.setSkuId(sku.getSkuId());
        message.setProductId(sku.getProductId());
        message.setMemberId(memberId);
        message.setMemberUsername(memberService.getCurrentUsername());
        message.setQuantity(1);
        message.setSeckillPrice(sku.getSeckillPrice());
        message.setOrderSn(orderSn);
        message.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        return message;
    }

    private SeckillActivity loadActivity(Long activityId) {
        SeckillActivity activity = redisUtil.getActivity(activityId);
        if (activity != null) {
            return activity;
        }
        activity = seckillDao.selectActivityById(activityId);
        if (activity != null) {
            redisUtil.putActivity(activity, calculateExpireSeconds(activity));
        }
        return activity;
    }

    private void validateActivityWindow(SeckillActivity activity) {
        Date now = new Date();
        if (activity.getStatus() != null && activity.getStatus() == 2) {
            Asserts.fail("秒杀活动已结束");
        }
        if (activity.getStartTime() != null && now.before(activity.getStartTime())) {
            Asserts.fail("秒杀活动未开始");
        }
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            Asserts.fail("秒杀活动已结束");
        }
    }

    private long calculateExpireSeconds(SeckillActivity activity) {
        long endMillis = activity.getEndTime() == null ? System.currentTimeMillis() : activity.getEndTime().getTime();
        long seconds = (endMillis - System.currentTimeMillis()) / 1000 + AFTER_END_EXTRA_SECONDS;
        return Math.max(seconds, 60L);
    }

    /**
     * 订单号：SK + 毫秒级时间戳 + 全局自增序列后四位，保证同一毫秒内也不重复
     */
    private String generateOrderSn() {
        Long sequence = stringRedisTemplate.opsForValue().increment(redisUtil.orderSnIncrKey());
        long suffix = sequence == null ? 0 : sequence % 10000;
        return "SK" + LocalDateTime.now().format(ORDER_SN_FORMATTER) + String.format("%04d", suffix);
    }
}
