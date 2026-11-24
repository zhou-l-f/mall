package com.macro.mall.seckill.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.seckill.domain.SeckillOrderMessage;

/**
 * 秒杀订单落库Service
 */
public interface SeckillOrderService {

    /**
     * 幂等创建秒杀订单
     *
     * @return 订单id
     */
    Long createOrder(SeckillOrderMessage message);

    /**
     * 按订单号查询订单
     */
    OmsOrder findByOrderSn(String orderSn);
}
