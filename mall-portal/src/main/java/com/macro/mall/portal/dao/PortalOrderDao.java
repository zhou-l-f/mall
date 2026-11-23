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
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute);

    /**
     * 批量修改订单状态
     */
    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 解除取消订单的库存锁定
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

}
