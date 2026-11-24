package com.macro.mall.seckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 秒杀应用启动入口
 * 独立部署，复用 mall-common / mall-mbg / mall-security
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.macro.mall")
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }

}
