package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台订单管理自定义Dao
 * Created by macro on 2018/9/4.
 */
public interface PortalOrderDao {
    /**
     * 获取订单及下单商品详情
     */
    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    /**
     * 原子锁定单个SKU库存：可用库存（stock-lock_stock）充足时才累加锁定库存
     * @return 影响行数，0 表示库存不足或SKU不存在
     */
    int lockSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 原子扣减单个SKU的真实库存并释放锁定库存：库存与锁定库存都充足时才扣减
     * @return 影响行数，0 表示库存不足或锁定库存不足
     */
    int deductSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 获取超时订单
     * @param minute 超时时间（分）
     * @param limit 单批最大订单数
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute, @Param("limit") Integer limit);

    /**
     * 原子将未支付订单更新为已支付
     * @return 影响行数，0表示订单不存在或已支付/已关闭
     */
    int updateOrderStatusToPaid(@Param("orderId") Long orderId, @Param("payType") Integer payType);

    /**
     * 原子将未支付订单更新为已关闭
     * @return 影响行数，0表示订单不存在或已支付/已关闭
     */
    int updateOrderStatusToCanceled(@Param("orderId") Long orderId);

    /**
     * 解除取消订单的库存锁定
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

}
