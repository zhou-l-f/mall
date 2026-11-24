package com.macro.mall.seckill.admin.dao;

import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.seckill.admin.domain.SeckillActivity;
import com.macro.mall.seckill.admin.domain.SeckillSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀后台配置Dao
 */
public interface SeckillAdminDao {

    SeckillActivity selectActivityById(@Param("id") Long id);

    List<SeckillActivity> selectActivityList(@Param("keyword") String keyword);

    int insertActivity(SeckillActivity activity);

    int updateActivity(SeckillActivity activity);

    int updateActivityStatus(@Param("id") Long id, @Param("status") Integer status);

    SeckillSku selectSkuById(@Param("id") Long id);

    SeckillSku selectSkuByActivityAndSku(@Param("activityId") Long activityId, @Param("skuId") Long skuId);

    List<SeckillSku> selectSkuList(@Param("activityId") Long activityId);

    int insertSku(SeckillSku sku);

    int updateSku(SeckillSku sku);

    PmsSkuStock selectSkuStock(@Param("skuId") Long skuId);

    /**
     * 从普通库存中原子划拨秒杀库存：可用库存充足时累加锁定库存
     */
    int lockSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 释放划拨的锁定库存
     */
    int releaseSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
