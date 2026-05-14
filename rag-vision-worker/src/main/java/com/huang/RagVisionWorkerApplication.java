package com.huang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author: huang
 * @Description: 脏活累活担当。默默在后台看图、解析 PDF，将结果打上标签存入 Redis
 * @DateTime: 2026/4/14 9:51
 **/
@SpringBootApplication
public class RagVisionWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagVisionWorkerApplication.class,args);
    }
}