package com.macro.mall.seckill.service;

import com.macro.mall.seckill.domain.SeckillActivityDetail;
import com.macro.mall.seckill.domain.SeckillResult;

/**
 * 秒杀抢购Service
 */
public interface SeckillService {

    /**
     * 执行秒杀抢购，成功后返回排队中的订单号
     */
    SeckillResult seckill(Long activityId, Long skuId);

    /**
     * 查询秒杀活动详情及商品列表
     */
    SeckillActivityDetail getActivityDetail(Long activityId);

    /**
     * 按订单号查询抢购结果
     */
    SeckillResult getResult(String orderSn);
}
