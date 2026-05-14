package com.huang.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/5/8 11:56
 **/
@Configuration
public class RestClientConfig {

    /**
     * 手动向 Spring 容器注入 RestClient.Builder
     * 专治后台 Worker 节点自动装配失效的问题
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
