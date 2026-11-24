package com.macro.mall.seckill.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀订单消息体
 * orderSn 在发消息前生成，作为生产、消费、查询三端的幂等键
 */
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long activityId;
    private Long skuId;
    private Long productId;
    private Long memberId;
    private String memberUsername;
    private Integer quantity;
    private BigDecimal seckillPrice;
    private String orderSn;
    private String traceId;

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getMemberUsername() {
        return memberUsername;
    }

    public void setMemberUsername(String memberUsername) {
        this.memberUsername = memberUsername;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public String getOrderSn() {
        return orderSn;
    }

    public void setOrderSn(String orderSn) {
        this.orderSn = orderSn;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String toString() {
        return "SeckillOrderMessage{" +
                "activityId=" + activityId +
                ", skuId=" + skuId +
                ", productId=" + productId +
                ", memberId=" + memberId +
                ", quantity=" + quantity +
                ", seckillPrice=" + seckillPrice +
                ", orderSn='" + orderSn + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
