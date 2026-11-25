package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.seckill.admin.dao.SeckillAdminDao;
import com.macro.mall.seckill.admin.domain.SeckillActivity;
import com.macro.mall.seckill.admin.domain.SeckillSku;
import com.macro.mall.service.SeckillAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 秒杀后台配置Service实现类
 */
@Service
public class SeckillAdminServiceImpl implements SeckillAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillAdminServiceImpl.class);

    /**
     * 秒杀配置变更广播频道，与 mall-seckill 订阅端保持一致
     */
    private static final String CONFIG_REFRESH_CHANNEL = "seckill:config:refresh";

    @Autowired
    private SeckillAdminDao seckillAdminDao;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public int createActivity(SeckillActivity activity) {
        validateActivity(activity);
        activity.setStatus(activity.getStatus() == null ? 0 : activity.getStatus());
        activity.setCreateTime(new Date());
        activity.setUpdateTime(new Date());
        int count = seckillAdminDao.insertActivity(activity);
        deleteActivityListCache();
        return count;
    }

    @Override
    @Transactional
    public int updateActivity(Long id, SeckillActivity activity) {
        SeckillActivity old = seckillAdminDao.selectActivityById(id);
        if (old == null) {
            Asserts.fail("秒杀活动不存在");
        }
        if (StrUtil.isBlank(activity.getName())) {
            activity.setName(old.getName());
        }
        if (activity.getStartTime() == null) {
            activity.setStartTime(old.getStartTime());
        }
        if (activity.getEndTime() == null) {
            activity.setEndTime(old.getEndTime());
        }
        if (activity.getStatus() == null) {
            activity.setStatus(old.getStatus());
        }
        validateActivity(activity);
        activity.setId(id);
        activity.setUpdateTime(new Date());
        int count = seckillAdminDao.updateActivity(activity);
        clearActivityKeys(id);
        return count;
    }

    @Override
    public List<SeckillActivity> listActivities(String keyword, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return seckillAdminDao.selectActivityList(keyword);
    }

    @Override
    @Transactional
    public int updateActivityStatus(Long id, Integer status) {
        SeckillActivity old = seckillAdminDao.selectActivityById(id);
        if (old == null) {
            Asserts.fail("秒杀活动不存在");
        }
        int count = seckillAdminDao.updateActivityStatus(id, status);
        clearActivityKeys(id);
        return count;
    }

    @Override
    @Transactional
    public int createSku(SeckillSku sku) {
        validateSku(sku);
        if (seckillAdminDao.selectActivityById(sku.getActivityId()) == null) {
            Asserts.fail("秒杀活动不存在");
        }
        if (seckillAdminDao.selectSkuStock(sku.getSkuId()) == null) {
            Asserts.fail("商品SKU不存在");
        }
        if (seckillAdminDao.selectSkuByActivityAndSku(sku.getActivityId(), sku.getSkuId()) != null) {
            Asserts.fail("该活动下已存在该秒杀商品");
        }
        //从普通库存中划拨秒杀库存，防止秒杀与普通订单合计超卖
        int locked = seckillAdminDao.lockSkuStock(sku.getSkuId(), sku.getSeckillStock());
        if (locked == 0) {
            Asserts.fail("商品可用库存不足，无法划拨秒杀库存");
        }
        sku.setStatus(sku.getStatus() == null ? 1 : sku.getStatus());
        sku.setCreateTime(new Date());
        sku.setUpdateTime(new Date());
        int count = seckillAdminDao.insertSku(sku);
        clearSkuKeys(sku.getActivityId(), sku.getSkuId());
        return count;
    }

    @Override
    @Transactional
    public int updateSku(Long id, SeckillSku sku) {
        SeckillSku old = seckillAdminDao.selectSkuById(id);
        if (old == null) {
            Asserts.fail("秒杀商品不存在");
        }
        if (sku.getSeckillPrice() == null) {
            sku.setSeckillPrice(old.getSeckillPrice());
        }
        if (sku.getSeckillStock() == null) {
            sku.setSeckillStock(old.getSeckillStock());
        }
        if (sku.getLimitCount() == null) {
            sku.setLimitCount(old.getLimitCount());
        }
        if (sku.getStatus() == null) {
            sku.setStatus(old.getStatus());
        }
        if (sku.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("秒杀价格必须大于0");
        }
        if (sku.getSeckillStock() < 0) {
            Asserts.fail("秒杀库存不能小于0");
        }
        if (sku.getLimitCount() < 1) {
            Asserts.fail("每人限购数量必须大于0");
        }
        int delta = sku.getSeckillStock() - old.getSeckillStock();
        if (delta > 0) {
            int locked = seckillAdminDao.lockSkuStock(old.getSkuId(), delta);
            if (locked == 0) {
                Asserts.fail("商品可用库存不足，无法增加秒杀库存");
            }
        } else if (delta < 0) {
            int released = seckillAdminDao.releaseSkuStock(old.getSkuId(), -delta);
            if (released == 0) {
                Asserts.fail("释放锁定库存失败");
            }
        }
        sku.setId(id);
        sku.setUpdateTime(new Date());
        int count = seckillAdminDao.updateSku(sku);
        clearSkuKeys(old.getActivityId(), old.getSkuId());
        return count;
    }

    @Override
    public List<SeckillSku> listSkus(Long activityId) {
        return seckillAdminDao.selectSkuList(activityId);
    }

    private void validateActivity(SeckillActivity activity) {
        if (StrUtil.isBlank(activity.getName())) {
            Asserts.fail("活动名称不能为空");
        }
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            Asserts.fail("活动开始/结束时间不能为空");
        }
        if (!activity.getStartTime().before(activity.getEndTime())) {
            Asserts.fail("活动开始时间必须早于结束时间");
        }
    }

    private void validateSku(SeckillSku sku) {
        if (sku.getActivityId() == null || sku.getSkuId() == null || sku.getProductId() == null) {
            Asserts.fail("活动、SKU、商品ID不能为空");
        }
        if (sku.getSeckillPrice() == null || sku.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("秒杀价格必须大于0");
        }
        if (sku.getSeckillStock() == null || sku.getSeckillStock() <= 0) {
            Asserts.fail("秒杀库存必须大于0");
        }
        if (sku.getLimitCount() == null || sku.getLimitCount() < 1) {
            Asserts.fail("每人限购数量必须大于0");
        }
    }

    /**
     * 活动信息或时间变更，清理活动 key 及其下所有 SKU 的预热 key
     */
    private void clearActivityKeys(Long activityId) {
        List<String> keys = new ArrayList<>();
        keys.add(activityKey(activityId));
        keys.add(activityListKey());
        keys.add(skuListKey(activityId));
        List<SeckillSku> skus = seckillAdminDao.selectSkuList(activityId);
        for (SeckillSku sku : skus) {
            keys.add(stockKey(activityId, sku.getSkuId()));
            keys.add(boughtKey(activityId, sku.getSkuId()));
            keys.add(skuKey(activityId, sku.getSkuId()));
        }
        try {
            stringRedisTemplate.delete(keys);
            publishRefresh("A:" + activityId);
        } catch (Exception e) {
            LOGGER.warn("清理秒杀活动缓存失败, activityId={}", activityId, e);
        }
    }

    private void clearSkuKeys(Long activityId, Long skuId) {
        try {
            List<String> keys = new ArrayList<>();
            keys.add(stockKey(activityId, skuId));
            keys.add(boughtKey(activityId, skuId));
            keys.add(skuKey(activityId, skuId));
            keys.add(skuListKey(activityId));
            stringRedisTemplate.delete(keys);
            publishRefresh("S:" + activityId + ":" + skuId);
        } catch (Exception e) {
            LOGGER.warn("清理秒杀商品缓存失败, activityId={}, skuId={}", activityId, skuId, e);
        }
    }

    /**
     * 删除前台活动列表缓存，新增活动后立即可见
     */
    private void deleteActivityListCache() {
        try {
            stringRedisTemplate.delete(activityListKey());
        } catch (Exception e) {
            LOGGER.warn("清理秒杀活动列表缓存失败", e);
        }
    }

    /**
     * 广播配置变更，通知抢购服务失效本地缓存；事务提交后再广播，避免订阅方回源读到旧数据
     */
    private void publishRefresh(String message) {
        Runnable task = () -> {
            try {
                stringRedisTemplate.convertAndSend(CONFIG_REFRESH_CHANNEL, message);
            } catch (Exception e) {
                LOGGER.warn("广播秒杀配置变更失败, message={}", message, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private String activityKey(Long activityId) {
        return "seckill:activity:{" + activityId + "}";
    }

    private String stockKey(Long activityId, Long skuId) {
        return "seckill:stock:{" + activityId + ":" + skuId + "}";
    }

    private String boughtKey(Long activityId, Long skuId) {
        return "seckill:bought:{" + activityId + ":" + skuId + "}";
    }

    private String skuKey(Long activityId, Long skuId) {
        return "seckill:sku:{" + activityId + ":" + skuId + "}";
    }

    private String activityListKey() {
        return "seckill:activity:list";
    }

    private String skuListKey(Long activityId) {
        return "seckill:sku:list:{" + activityId + "}";
    }
}
