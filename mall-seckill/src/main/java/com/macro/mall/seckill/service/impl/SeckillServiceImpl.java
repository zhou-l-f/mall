package com.macro.mall.seckill.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.seckill.component.SeckillLocalCache;
import com.macro.mall.seckill.component.SeckillOrderSender;
import com.macro.mall.seckill.dao.SeckillDao;
import com.macro.mall.seckill.domain.*;
import com.macro.mall.seckill.security.SeckillMemberService;
import com.macro.mall.seckill.service.SeckillService;
import com.macro.mall.seckill.util.SeckillRedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final SeckillLocalCache localCache;
    private final SeckillMemberService memberService;
    private final SeckillOrderSender orderSender;

    @Value("${seckill.result-expire-seconds:86400}")
    private long resultExpireSeconds;

    public SeckillServiceImpl(SeckillDao seckillDao, SeckillRedisUtil redisUtil, SeckillLocalCache localCache,
                              SeckillMemberService memberService, SeckillOrderSender orderSender) {
        this.seckillDao = seckillDao;
        this.redisUtil = redisUtil;
        this.localCache = localCache;
        this.memberService = memberService;
        this.orderSender = orderSender;
    }

    @Override
    public SeckillResult seckill(Long activityId, Long skuId) {
        Long memberId = memberService.getCurrentMemberId();
        if (memberId == null) {
            Asserts.fail("请先登录");
        }
        SeckillActivity activity = loadActivity(activityId);
        if (activity == null) {
            return SeckillResult.failed("秒杀活动不存在");
        }
        String windowError = checkActivityWindow(activity);
        if (windowError != null) {
            return SeckillResult.failed(windowError);
        }
        long expireSeconds = calculateExpireSeconds(activity);
        SeckillSku sku = loadSku(activityId, skuId, expireSeconds);
        if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
            return SeckillResult.failed("秒杀商品不存在或已下架");
        }
        long nowMillis = System.currentTimeMillis();
        long startMillis = activity.getStartTime() == null ? 0L : activity.getStartTime().getTime();
        long endMillis = activity.getEndTime() == null ? 0L : activity.getEndTime().getTime();
        int limitCount = sku.getLimitCount() == null ? 1 : sku.getLimitCount();
        String orderSnPrefix = "SK" + LocalDateTime.now().format(ORDER_SN_FORMATTER);
        //活动窗口校验、库存预检、限购校验、扣减、订单号生成、结果写入在一次 Lua 往返内完成
        String requestId = UUID.randomUUID().toString().replace("-", "");
        SeckillRedisUtil.SeckillScriptResult scriptResult;
        try {
            scriptResult = redisUtil.executeSeckill(activityId, skuId, memberId, limitCount,
                    sku.getSeckillStock(), expireSeconds, resultExpireSeconds,
                    nowMillis, startMillis, endMillis, orderSnPrefix, requestId);
        } catch (Exception e) {
            //超时等异常下脚本可能已在 Redis 执行完，按请求标记恢复订单号，避免库存永久损耗
            String recoveredOrderSn = recoverOrderSn(requestId, e);
            if (recoveredOrderSn == null) {
                return SeckillResult.failed("抢购失败，请稍后重试");
            }
            scriptResult = new SeckillRedisUtil.SeckillScriptResult(SeckillRedisUtil.SUCCESS, recoveredOrderSn);
        }
        long code = scriptResult.code();
        if (code != SeckillRedisUtil.SUCCESS) {
            //售罄、限购等属于预期内高频结果，直接返回失败对象，不抛异常避免栈填充开销
            return SeckillResult.failed(scriptFailMessage(code));
        }
        String orderSn = scriptResult.orderSn();
        try {
            SeckillOrderMessage message = buildMessage(activity, sku, memberId, orderSn);
            orderSender.send(message);
        } catch (Exception e) {
            //库存已在 Lua 内扣减，同步投递异常不会触发 confirm 回调，这里兜底回补，避免库存永久损耗
            LOGGER.error("秒杀订单消息投递失败，回补库存, orderSn={}, error={}", orderSn, e.toString());
            orderSender.compensate(orderSn, activityId, skuId, memberId, 1);
            return SeckillResult.failed("抢购失败，请稍后重试");
        }
        LOGGER.debug("秒杀抢购成功, orderSn={}, activityId={}, skuId={}, memberId={}",
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

    /**
     * 活动配置：本地缓存 -> Redis -> DB，逐级回源并回填
     */
    private SeckillActivity loadActivity(Long activityId) {
        SeckillActivity activity = localCache.getActivity(activityId);
        if (activity != null) {
            return activity;
        }
        activity = redisUtil.getActivity(activityId);
        if (activity == null) {
            activity = seckillDao.selectActivityById(activityId);
            if (activity != null) {
                redisUtil.putActivity(activity, calculateExpireSeconds(activity));
            }
        }
        if (activity != null) {
            localCache.putActivity(activity);
        }
        return activity;
    }

    /**
     * 商品配置：本地缓存 -> Redis -> DB，逐级回源并回填；后台变更配置时会广播失效本地缓存
     */
    private SeckillSku loadSku(Long activityId, Long skuId, long expireSeconds) {
        SeckillSku sku = localCache.getSku(activityId, skuId);
        if (sku != null) {
            return sku;
        }
        sku = redisUtil.getSku(activityId, skuId);
        if (sku == null) {
            sku = seckillDao.selectSku(activityId, skuId);
            if (sku != null) {
                redisUtil.putSku(sku, expireSeconds);
            }
        }
        if (sku != null) {
            localCache.putSku(sku);
        }
        return sku;
    }

    /**
     * 脚本结果未知时按请求标记恢复订单号，标记不存在说明脚本未执行、未产生扣减
     * 客户端超时不会取消已发出的命令，短暂轮询避免命令稍后才执行导致漏判
     */
    private String recoverOrderSn(String requestId, Exception cause) {
        for (int i = 0; i < 3; i++) {
            try {
                String orderSn = redisUtil.getScriptOrderSn(requestId);
                if (orderSn != null) {
                    LOGGER.warn("秒杀脚本已执行但响应异常，按恢复出的订单号继续投递, orderSn={}, error={}",
                            orderSn, cause.toString());
                    return orderSn;
                }
            } catch (Exception e) {
                LOGGER.warn("恢复秒杀请求结果失败, requestId={}, error={}", requestId, e.toString());
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOGGER.warn("秒杀脚本执行异常且未产生扣减, requestId={}, error={}", requestId, cause.toString());
        return null;
    }

    /**
     * 活动窗口预校验，返回失败原因，通过时返回 null
     */
    private String checkActivityWindow(SeckillActivity activity) {
        Date now = new Date();
        if (activity.getStatus() != null && activity.getStatus() == 2) {
            return "秒杀活动已结束";
        }
        if (activity.getStartTime() != null && now.before(activity.getStartTime())) {
            return "秒杀活动未开始";
        }
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            return "秒杀活动已结束";
        }
        return null;
    }

    /**
     * 脚本结果码转失败原因
     */
    private String scriptFailMessage(long code) {
        if (code == SeckillRedisUtil.SOLD_OUT) {
            return "商品已售罄";
        }
        if (code == SeckillRedisUtil.LIMIT_EXCEEDED) {
            return "超出限购数量";
        }
        if (code == SeckillRedisUtil.NOT_STARTED) {
            return "秒杀活动未开始";
        }
        if (code == SeckillRedisUtil.ACTIVITY_ENDED) {
            return "秒杀活动已结束";
        }
        return "抢购失败，请稍后重试";
    }

    private long calculateExpireSeconds(SeckillActivity activity) {
        long endMillis = activity.getEndTime() == null ? System.currentTimeMillis() : activity.getEndTime().getTime();
        long seconds = (endMillis - System.currentTimeMillis()) / 1000 + AFTER_END_EXTRA_SECONDS;
        return Math.max(seconds, 60L);
    }
}
