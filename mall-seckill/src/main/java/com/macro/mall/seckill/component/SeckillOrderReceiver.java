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
 * 秒杀订单消费者
 * 手动 ack；幂等键为 orderSn；失败重试 2 次后进入死信队列
 */
@Component
public class SeckillOrderReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderReceiver.class);

    private static final int MAX_RETRY = 2;

    private final SeckillOrderService seckillOrderService;
    private final SeckillRedisUtil redisUtil;

    @Value("${seckill.result-expire-seconds:86400}")
    private long resultExpireSeconds;

    public SeckillOrderReceiver(SeckillOrderService seckillOrderService, SeckillRedisUtil redisUtil) {
        this.seckillOrderService = seckillOrderService;
        this.redisUtil = redisUtil;
    }

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_QUEUE)
    public void onMessage(SeckillOrderMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String orderSn = message.getOrderSn();
        try {
            OmsOrder existing = seckillOrderService.findByOrderSn(orderSn);
            if (existing != null) {
                LOGGER.info("秒杀订单已存在，跳过重复消息, orderSn={}", orderSn);
                markSuccess(message, existing);
                channel.basicAck(deliveryTag, false);
                return;
            }
            Long orderId = seckillOrderService.createOrder(message);
            OmsOrder order = new OmsOrder();
            order.setId(orderId);
            markSuccess(message, order);
            redisUtil.deleteRetry(orderSn);
            channel.basicAck(deliveryTag, false);
            LOGGER.info("秒杀订单消息处理成功, orderSn={}, orderId={}", orderSn, orderId);
        } catch (Exception e) {
            long retry = redisUtil.incrementRetry(orderSn);
            LOGGER.error("秒杀订单消息处理失败, orderSn={}, retry={}", orderSn, retry, e);
            try {
                if (retry <= MAX_RETRY) {
                    Thread.sleep(200L);
                    channel.basicNack(deliveryTag, false, true);
                } else {
                    // 超过重试次数，投递死信队列，由补偿消费者回补库存
                    channel.basicNack(deliveryTag, false, false);
                }
            } catch (Exception nackException) {
                LOGGER.error("秒杀订单消息 ack/nack 异常, orderSn={}", orderSn, nackException);
            }
        }
    }

    private void markSuccess(SeckillOrderMessage message, OmsOrder order) {
        BigDecimal payAmount = message.getSeckillPrice()
                .multiply(BigDecimal.valueOf(message.getQuantity()));
        redisUtil.setResultObject(SeckillResult.success(message.getOrderSn(), order.getId(), payAmount),
                resultExpireSeconds);
    }
}
