package com.macro.mall.service;

import com.macro.mall.seckill.admin.domain.SeckillActivity;
import com.macro.mall.seckill.admin.domain.SeckillSku;

import java.util.List;

/**
 * 秒杀后台配置Service
 */
public interface SeckillAdminService {

    int createActivity(SeckillActivity activity);

    int updateActivity(Long id, SeckillActivity activity);

    List<SeckillActivity> listActivities(String keyword, Integer pageNum, Integer pageSize);

    int updateActivityStatus(Long id, Integer status);

    int createSku(SeckillSku sku);

    int updateSku(Long id, SeckillSku sku);

    List<SeckillSku> listSkus(Long activityId);
}
