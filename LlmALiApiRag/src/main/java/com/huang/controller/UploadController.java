package com.huang.controller;

import com.huang.event.DocumentParseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/17 14:50
 **/
@Slf4j
@RestController
@RequestMapping("/api/doc")
public class UploadController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UploadController(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        String taskId = UUID.randomUUID().toString();

        try {
            // 1. 将文件上传到 OSS (伪代码)
            String fileUrl = mockUploadToOss(file);

            // 2. 组装跨微服务事件实体 (来自 common 模块)
            DocumentParseEvent event = new DocumentParseEvent(
                    taskId, file.getOriginalFilename(), fileUrl, "admin_01"
            );

            // 3. 将任务丢入 Kafka 主题，主线程瞬间返回！
            kafkaTemplate.send("rag-vision-parsing-topic", taskId, event);

            log.info("📢 文件已接收，任务 [{}] 已推送到 Kafka", taskId);
            return "文件上传成功，AI正在后台疯狂解析中，任务ID: " + taskId;

        } catch (Exception e) {
            return "上传失败: " + e.getMessage();
        }
    }
}