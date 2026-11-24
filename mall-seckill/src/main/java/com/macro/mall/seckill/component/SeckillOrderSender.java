package com.macro.mall.seckill.component;

import com.macro.mall.seckill.config.RabbitMqConfig;
import com.macro.mall.seckill.domain.SeckillOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息发送者
 */
@Component
public class SeckillOrderSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderSender.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void send(SeckillOrderMessage message) {
        CorrelationData correlationData = new CorrelationData(message.getOrderSn());
        rabbitTemplate.convertAndSend(RabbitMqConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY, message, correlationData);
        LOGGER.info("秒杀订单消息已发送, orderSn={}, skuId={}, memberId={}",
                message.getOrderSn(), message.getSkuId(), message.getMemberId());
    }
}
