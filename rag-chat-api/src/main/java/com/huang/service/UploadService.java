package com.huang.service;

import com.huang.event.DocumentParseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/5/7 16:13
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 定义本地存储路径：存在当前项目根目录下的 rag-files 文件夹中
    private final String STORAGE_DIR = System.getProperty("user.dir") + File.separator + "rag-files" + File.separator;
    // Kafka 的 Topic 名称 (刚才自动建好的那个)
    private final String KAFKA_TOPIC = "document-parse-topic";

    /**
     * 处理文件上传并异步发送 Kafka 消息
     */
    public String uploadAndSendTask(MultipartFile file, String uploaderId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "unknown.pdf";
        }

        // 1. 读取文件字节并计算 MD5 (用于防重复存盘，以及作为后续向量库的关联标识)
        byte[] fileBytes = file.getBytes();
        String md5 = DigestUtils.md5DigestAsHex(fileBytes);
        log.info("接收到文件: {}, MD5: {}", originalFilename, md5);

        // 2. 检查并创建存储目录 (如果 rag-files 文件夹不存在，会自动创建)
        Path storagePath = Paths.get(STORAGE_DIR);
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }

        // 3. 构建目标文件路径 (将文件名重命名为 MD5 值，防止中文乱码，保留原始后缀)
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }
        String savedFileName = md5 + extension;
        File destFile = new File(STORAGE_DIR + savedFileName);

        // 4. 保存文件到本地磁盘
        if (!destFile.exists()) {
            file.transferTo(destFile);
            log.info("文件已成功保存到本地磁盘: {}", destFile.getAbsolutePath());
        } else {
            log.info("本地已存在相同文件，跳过存盘步骤: {}", destFile.getAbsolutePath());
        }

        // 5. 构建 Kafka 消息事件实体 (这个类我们在 rag-common 里写好了)
        DocumentParseEvent event = new DocumentParseEvent(
                md5,
                destFile.getAbsolutePath(),
                originalFilename,
                uploaderId
        );

        // 6. 发送消息到 Kafka (把 md5 作为 key 传进去，保证同一文件的消息有序)
        kafkaTemplate.send(KAFKA_TOPIC, md5, event);
        log.info("🚀 成功发送解析任务到 Kafka Topic [{}], 事件内容: {}", KAFKA_TOPIC, event);

        // 返回文件的唯一标识给前端
        return md5;
    }
}