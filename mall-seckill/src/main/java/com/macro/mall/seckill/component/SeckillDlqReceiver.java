package com.macro.mall.seckill.component;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.seckill.config.RabbitMqConfig;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import com.macro.mall.seckill.domain.SeckillResult;
import com.macro.mall.seckill.service.SeckillOrderService;
import com.macro.mall.seckill.util.SeckillRedisUtil;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 秒杀死信消费者
 * 主消费者重试仍失败后，回补 Redis 库存与限购计数，并将抢购结果标记为失败
 */
@Component
public class SeckillDlqReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillDlqReceiver.class);

    private final SeckillOrderService seckillOrderService;
    private final SeckillRedisUtil redisUtil;

    @Value("${seckill.result-expire-seconds:86400}")
    private long resultExpireSeconds;

    public SeckillDlqReceiver(SeckillOrderService seckillOrderService, SeckillRedisUtil redisUtil) {
        this.seckillOrderService = seckillOrderService;
        this.redisUtil = redisUtil;
    }

    @RabbitListener(queues = RabbitMqConfig.SECKILL_DLQ_QUEUE)
    public void onDeadLetter(SeckillOrderMessage message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String orderSn = message.getOrderSn();
        try {
            OmsOrder existing = seckillOrderService.findByOrderSn(orderSn);
            if (existing != null) {
                BigDecimal payAmount = message.getSeckillPrice()
                        .multiply(BigDecimal.valueOf(message.getQuantity()));
                redisUtil.setResultObject(SeckillResult.success(orderSn, existing.getId(), payAmount),
                        resultExpireSeconds);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!redisUtil.markCompensated(orderSn)) {
                LOGGER.info("秒杀订单已补偿，跳过重复死信, orderSn={}", orderSn);
                channel.basicAck(deliveryTag, false);
                return;
            }
            redisUtil.incrStock(message.getActivityId(), message.getSkuId(), message.getQuantity());
            redisUtil.decrBought(message.getActivityId(), message.getSkuId(),
                    message.getMemberId(), message.getQuantity());
            redisUtil.deleteRetry(orderSn);
            redisUtil.setResultObject(SeckillResult.failed(orderSn, "订单创建失败，库存已回补"),
                    resultExpireSeconds);
            channel.basicAck(deliveryTag, false);
            LOGGER.warn("秒杀死信补偿完成, orderSn={}, skuId={}, memberId={}, quantity={}",
                    orderSn, message.getSkuId(), message.getMemberId(), message.getQuantity());
        } catch (Exception e) {
            LOGGER.error("秒杀死信补偿异常, orderSn={}", orderSn, e);
            try {
                //避免异常死信无限循环，记录后确认，由对账任务兜底
                channel.basicAck(deliveryTag, false);
            } catch (Exception ackException) {
                LOGGER.error("秒杀死信 ack 异常, orderSn={}", orderSn, ackException);
            }
        }
    }
}
