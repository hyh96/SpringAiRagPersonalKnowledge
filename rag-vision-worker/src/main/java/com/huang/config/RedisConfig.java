package com.huang.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/13 14:19
 **/
//@Configuration
//public class RedisConfig {
//
//    @Value("${spring.ai.vectorstore.redis.uri:redis://localhost:6379}")
//    private String redisUri;
//
//    // @Bean 注解相当于发了一个招聘启事：告诉 Spring 把这个方法的返回值收编进容器
//    @Bean
//    public JedisPooled jedisPooled() throws URISyntaxException {
//        return new JedisPooled(new URI(redisUri));
//    }
//}