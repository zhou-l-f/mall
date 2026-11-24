package com.macro.mall.seckill.config;

import com.macro.mall.seckill.component.SeckillCacheRefreshListener;
import com.macro.mall.seckill.util.SeckillRedisUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 秒杀配置变更广播订阅
 */
@Configuration
public class SeckillLocalCacheConfig {

    @Bean
    public RedisMessageListenerContainer seckillLocalCacheContainer(RedisConnectionFactory connectionFactory,
                                                                    SeckillCacheRefreshListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(SeckillRedisUtil.CONFIG_REFRESH_CHANNEL));
        return container;
    }
}
