package com.macro.mall.seckill.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀订单消息队列配置
 * 主队列消费失败后进入死信队列，由补偿消费者回补 Redis 库存与防重计数
 */
@Configuration
public class RabbitMqConfig {

    /**
     * 秒杀订单交换机
     */
    public static final String SECKILL_ORDER_EXCHANGE = "mall.seckill.direct";
    /**
     * 秒杀订单队列
     */
    public static final String SECKILL_ORDER_QUEUE = "mall.seckill.order";
    /**
     * 秒杀订单路由键
     */
    public static final String SECKILL_ORDER_ROUTING_KEY = "mall.seckill.order";
    /**
     * 死信交换机
     */
    public static final String SECKILL_DLX_EXCHANGE = "mall.seckill.direct.dlx";
    /**
     * 死信队列
     */
    public static final String SECKILL_DLQ_QUEUE = "mall.seckill.order.dlq";
    /**
     * 死信路由键
     */
    public static final String SECKILL_DLQ_ROUTING_KEY = "mall.seckill.order.dlq";

    @Bean
    public DirectExchange seckillOrderExchange() {
        return ExchangeBuilder.directExchange(SECKILL_ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange seckillDlxExchange() {
        return ExchangeBuilder.directExchange(SECKILL_DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SECKILL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue seckillOrderDlqQueue() {
        return QueueBuilder.durable(SECKILL_DLQ_QUEUE).build();
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue()).to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding seckillDlqBinding() {
        return BindingBuilder.bind(seckillOrderDlqQueue()).to(seckillDlxExchange())
                .with(SECKILL_DLQ_ROUTING_KEY);
    }

    /**
     * 消息体使用 JSON 序列化，便于排查与跨语言消费
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
