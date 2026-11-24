package com.macro.mall.seckill.dao;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.seckill.domain.SeckillActivity;
import com.macro.mall.seckill.domain.SeckillSku;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 秒杀模块自定义Dao
 */
public interface SeckillDao {

    /**
     * 查询活动
     */
    SeckillActivity selectActivityById(@Param("id") Long id);

    /**
     * 查询需要预热的进行中/待开始活动
     *
     * @param now          当前时间
     * @param aheadMinutes 提前预热的分钟数
     * @param limit        单批最大活动数
     */
    List<SeckillActivity> selectActivitiesForPreheat(@Param("now") Date now,
                                                     @Param("aheadMinutes") int aheadMinutes,
                                                     @Param("limit") int limit);

    /**
     * 查询活动下的秒杀商品
     */
    List<SeckillSku> selectSkusByActivityId(@Param("activityId") Long activityId);

    /**
     * 查询单个秒杀商品
     */
    SeckillSku selectSku(@Param("activityId") Long activityId, @Param("skuId") Long skuId);

    /**
     * 条件扣减秒杀库存（落库确认时使用），库存充足才扣减
     *
     * @return 影响行数，0 表示库存不足
     */
    int deductSeckillStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 回补秒杀库存
     */
    int restoreSeckillStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 按订单号统计订单数（幂等校验）
     */
    int countOrderBySn(@Param("orderSn") String orderSn);

    /**
     * 按订单号查询订单
     */
    OmsOrder selectOrderBySn(@Param("orderSn") String orderSn);
}
