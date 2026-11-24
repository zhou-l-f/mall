package com.macro.mall.seckill.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.seckill.dao.SeckillDao;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import com.macro.mall.seckill.domain.SeckillSku;
import com.macro.mall.seckill.service.SeckillOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 秒杀订单落库Service实现类
 */
@Service
public class SeckillOrderServiceImpl implements SeckillOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderServiceImpl.class);

    private final SeckillDao seckillDao;
    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper orderItemMapper;
    private final PmsProductMapper productMapper;
    private final PmsSkuStockMapper skuStockMapper;
    private final UmsMemberMapper memberMapper;
    private final ObjectMapper objectMapper;

    public SeckillOrderServiceImpl(SeckillDao seckillDao, OmsOrderMapper orderMapper,
                                   OmsOrderItemMapper orderItemMapper, PmsProductMapper productMapper,
                                   PmsSkuStockMapper skuStockMapper, UmsMemberMapper memberMapper,
                                   ObjectMapper objectMapper) {
        this.seckillDao = seckillDao;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.skuStockMapper = skuStockMapper;
        this.memberMapper = memberMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Long createOrder(SeckillOrderMessage message) {
        OmsOrder existing = seckillDao.selectOrderBySn(message.getOrderSn());
        if (existing != null) {
            return existing.getId();
        }
        SeckillSku sku = seckillDao.selectSku(message.getActivityId(), message.getSkuId());
        if (sku == null) {
            throw new IllegalStateException("秒杀商品不存在, skuId=" + message.getSkuId());
        }
        int deducted = seckillDao.deductSeckillStock(sku.getId(), message.getQuantity());
        if (deducted == 0) {
            throw new IllegalStateException("秒杀库存不足, seckillSkuId=" + sku.getId());
        }
        PmsProduct product = productMapper.selectByPrimaryKey(message.getProductId());
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(message.getSkuId());
        UmsMember member = memberMapper.selectByPrimaryKey(message.getMemberId());
        BigDecimal quantity = BigDecimal.valueOf(message.getQuantity());
        BigDecimal amount = message.getSeckillPrice().multiply(quantity);

        OmsOrder order = buildOrder(message, member, amount);
        orderMapper.insertSelective(order);

        OmsOrderItem item = buildOrderItem(message, product, skuStock, amount);
        item.setOrderId(order.getId());
        orderItemMapper.insertSelective(item);

        LOGGER.info("秒杀订单落库成功, orderSn={}, orderId={}", message.getOrderSn(), order.getId());
        return order.getId();
    }

    @Override
    public OmsOrder findByOrderSn(String orderSn) {
        return seckillDao.selectOrderBySn(orderSn);
    }

    private OmsOrder buildOrder(SeckillOrderMessage message, UmsMember member, BigDecimal amount) {
        OmsOrder order = new OmsOrder();
        order.setMemberId(message.getMemberId());
        order.setMemberUsername(message.getMemberUsername());
        order.setOrderSn(message.getOrderSn());
        order.setCreateTime(new Date());
        order.setTotalAmount(amount);
        order.setPayAmount(amount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPromotionAmount(BigDecimal.ZERO);
        order.setIntegrationAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayType(0);
        order.setSourceType(1);
        order.setStatus(0);
        order.setOrderType(1);
        order.setAutoConfirmDay(15);
        order.setIntegration(0);
        order.setGrowth(0);
        order.setPromotionInfo(buildPromotionInfo(message));
        order.setBillType(0);
        order.setConfirmStatus(0);
        order.setDeleteStatus(0);
        order.setUseIntegration(0);
        order.setNote("秒杀订单");
        //秒杀订单异步创建时用户尚未确认收货地址，先用会员基础信息填充非空收货字段
        String receiverName = message.getMemberUsername();
        String receiverPhone = "";
        if (member != null) {
            if (member.getNickname() != null && !member.getNickname().isEmpty()) {
                receiverName = member.getNickname();
            }
            if (member.getPhone() != null) {
                receiverPhone = member.getPhone();
            }
        }
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverProvince("");
        order.setReceiverCity("");
        order.setReceiverRegion("");
        order.setReceiverDetailAddress("");
        order.setReceiverPostCode("");
        return order;
    }

    private OmsOrderItem buildOrderItem(SeckillOrderMessage message, PmsProduct product,
                                        PmsSkuStock skuStock, BigDecimal amount) {
        OmsOrderItem item = new OmsOrderItem();
        item.setOrderSn(message.getOrderSn());
        item.setProductId(message.getProductId());
        item.setProductSkuId(message.getSkuId());
        if (product != null) {
            item.setProductName(product.getName());
            item.setProductPic(product.getPic());
            item.setProductSn(product.getProductSn());
            item.setProductBrand(product.getBrandName());
            item.setProductCategoryId(product.getProductCategoryId());
        }
        if (skuStock != null) {
            item.setProductSkuCode(skuStock.getSkuCode());
            if (item.getProductPic() == null) {
                item.setProductPic(skuStock.getPic());
            }
        }
        item.setProductPrice(message.getSeckillPrice());
        item.setProductQuantity(message.getQuantity());
        item.setPromotionName("秒杀活动");
        item.setPromotionAmount(BigDecimal.ZERO);
        item.setCouponAmount(BigDecimal.ZERO);
        item.setIntegrationAmount(BigDecimal.ZERO);
        item.setRealAmount(amount);
        item.setGiftIntegration(0);
        item.setGiftGrowth(0);
        return item;
    }

    private String buildPromotionInfo(SeckillOrderMessage message) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activityId", message.getActivityId());
        info.put("skuId", message.getSkuId());
        info.put("seckillPrice", message.getSeckillPrice());
        info.put("traceId", message.getTraceId());
        try {
            return objectMapper.writeValueAsString(info);
        } catch (Exception e) {
            return "seckill";
        }
    }
}
