package com.macro.mall.seckill.component;

import com.macro.mall.seckill.config.RabbitMqConfig;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import com.macro.mall.seckill.domain.SeckillResult;
import com.macro.mall.seckill.util.SeckillRedisUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息发送者
 * 开启生产者确认，消息未到达 Broker 时回补 Redis 库存并标记失败
 */
@Component
public class SeckillOrderSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderSender.class);

    private final RabbitTemplate rabbitTemplate;
    private final SeckillRedisUtil redisUtil;

    @Value("${seckill.result-expire-seconds:86400}")
    private long resultExpireSeconds;

    public SeckillOrderSender(RabbitTemplate rabbitTemplate, SeckillRedisUtil redisUtil) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisUtil = redisUtil;
    }

    @PostConstruct
    public void initCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack || correlationData == null) {
                return;
            }
            LOGGER.error("秒杀消息未被Broker确认, correlationId={}, cause={}",
                    correlationData.getId(), cause);
            compensatePublishFailure(correlationData.getId());
        });
        rabbitTemplate.setReturnsCallback(returned -> LOGGER.error("秒杀消息路由失败, replyText={}, exchange={}, routingKey={}",
                returned.getReplyText(), returned.getExchange(), returned.getRoutingKey()));
    }

    public void send(SeckillOrderMessage message) {
        String correlationId = message.getOrderSn() + "|" + message.getActivityId() + "|"
                + message.getSkuId() + "|" + message.getMemberId() + "|" + message.getQuantity();
        CorrelationData correlationData = new CorrelationData(correlationId);
        rabbitTemplate.convertAndSend(RabbitMqConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY, message, correlationData);
        LOGGER.info("秒杀订单消息已发送, orderSn={}, skuId={}, memberId={}",
                message.getOrderSn(), message.getSkuId(), message.getMemberId());
    }

    /**
     * 投递失败补偿（幂等）：回补 Redis 库存与限购计数，结果标记为失败
     * 同步发送异常与 Broker 未确认两条路径共用，避免重复回补
     */
    public void compensate(String orderSn, Long activityId, Long skuId, Long memberId, int quantity) {
        try {
            if (!redisUtil.markCompensated(orderSn)) {
                return;
            }
            redisUtil.incrStock(activityId, skuId, quantity);
            redisUtil.decrBought(activityId, skuId, memberId, quantity);
            redisUtil.setResultObject(SeckillResult.failed(orderSn, "消息投递失败"), resultExpireSeconds);
            LOGGER.warn("秒杀消息投递失败，已回补库存, orderSn={}, quantity={}", orderSn, quantity);
        } catch (Exception e) {
            LOGGER.error("秒杀消息投递失败补偿异常, orderSn={}", orderSn, e);
        }
    }

    /**
     * 发布失败：回补 Redis 库存与限购计数，结果标记为失败
     */
    private void compensatePublishFailure(String correlationId) {
        try {
            String[] parts = correlationId.split("\\|");
            if (parts.length < 5) {
                return;
            }
            compensate(parts[0], Long.valueOf(parts[1]), Long.valueOf(parts[2]),
                    Long.valueOf(parts[3]), Integer.parseInt(parts[4]));
        } catch (Exception e) {
            LOGGER.error("解析秒杀消息补偿信息异常, correlationId={}", correlationId, e);
        }
    }
}
