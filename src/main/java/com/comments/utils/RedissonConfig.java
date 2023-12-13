package com.comments.utils;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 *  TODO
 * </p>
 *
 * @author Colin
 * @since 2023/12/7
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://8.140.38.47:6379").setPassword("dengzuxuan");
        return Redisson.create(config);
    }
}