package com.macro.mall.seckill.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 秒杀活动详情（含商品列表）
 */
public class SeckillActivityDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private SeckillActivity activity;
    private List<SeckillSku> skus;

    public SeckillActivity getActivity() {
        return activity;
    }

    public void setActivity(SeckillActivity activity) {
        this.activity = activity;
    }

    public List<SeckillSku> getSkus() {
        return skus;
    }

    public void setSkus(List<SeckillSku> skus) {
        this.skus = skus;
    }
}
