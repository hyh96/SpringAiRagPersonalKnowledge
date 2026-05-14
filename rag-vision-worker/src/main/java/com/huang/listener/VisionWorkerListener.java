package com.huang.listener;

import com.huang.event.DocumentParseEvent;
import com.huang.service.PdfVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @Author: huang
 * @Description: 监听kafka并调用云端视觉模型
 * @DateTime: 2026/4/17 15:12
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionWorkerListener {

    private final PdfVectorService pdfVectorService;

    /**
     * 监听解析主题，实现异步削峰
     * groupId 对应 application.yml 里的配置
     */
    @KafkaListener(topics = "document-parse-topic", groupId = "rag-document-parse-group")
    public void onMessage(DocumentParseEvent event) {
        log.info("📥 [消费者] 监听到文件解析任务！文件名: [{}], MD5: [{}]", event.getOriginalFileName(), event.getFileMd5());

        long startTime = System.currentTimeMillis();
        try {
            // 调用核心解析服务
            pdfVectorService.processAndStore(
                    event.getFilePath(),
                    event.getFileMd5(),
                    event.getOriginalFileName(),
                    event.getUploaderId()
            );
            log.info("🏁 任务处理完毕！耗时: {} ms", (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            log.error("❌ 处理文档事件失败: {}", event.getFileMd5(), e);
        }
    }
}