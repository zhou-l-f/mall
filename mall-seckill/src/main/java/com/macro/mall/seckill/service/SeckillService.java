package com.macro.mall.seckill.service;

import com.macro.mall.seckill.domain.SeckillActivity;
import com.macro.mall.seckill.domain.SeckillActivityDetail;
import com.macro.mall.seckill.domain.SeckillResult;
import com.macro.mall.seckill.domain.SeckillSku;

import java.util.List;

/**
 * 秒杀抢购Service
 */
public interface SeckillService {

    /**
     * 执行秒杀抢购，成功返回排队中的订单号；售罄、限购等业务失败返回 status=FAILED 与原因，不抛业务异常
     */
    SeckillResult seckill(Long activityId, Long skuId);

    /**
     * 查询前台展示用活动列表（未结束的活动）
     */
    List<SeckillActivity> listActivities();

    /**
     * 查询活动下启用中的秒杀商品列表，用于前台展示
     */
    List<SeckillSku> listSkus(Long activityId);

    /**
     * 查询秒杀活动详情及商品列表
     */
    SeckillActivityDetail getActivityDetail(Long activityId);

    /**
     * 按订单号查询抢购结果
     */
    SeckillResult getResult(String orderSn);
}
