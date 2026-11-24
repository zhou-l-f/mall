package com.macro.mall.seckill.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀抢购结果
 */
public class SeckillResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_QUEUING = "QUEUING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * QUEUING / SUCCESS / FAILED
     */
    private String status;
    private String orderSn;
    private Long orderId;
    private BigDecimal payAmount;
    /**
     * 失败原因
     */
    private String reason;

    public static SeckillResult queuing(String orderSn) {
        SeckillResult result = new SeckillResult();
        result.setStatus(STATUS_QUEUING);
        result.setOrderSn(orderSn);
        return result;
    }

    public static SeckillResult success(String orderSn, Long orderId, BigDecimal payAmount) {
        SeckillResult result = new SeckillResult();
        result.setStatus(STATUS_SUCCESS);
        result.setOrderSn(orderSn);
        result.setOrderId(orderId);
        result.setPayAmount(payAmount);
        return result;
    }

    public static SeckillResult failed(String orderSn, String reason) {
        SeckillResult result = new SeckillResult();
        result.setStatus(STATUS_FAILED);
        result.setOrderSn(orderSn);
        result.setReason(reason);
        return result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
