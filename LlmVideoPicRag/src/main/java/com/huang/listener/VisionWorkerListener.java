package com.huang.listener;

import com.huang.event.DocumentParseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/17 15:12
 **/
@Slf4j
@Service
public class VisionWorkerListener {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public VisionWorkerListener(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        // 这里的 chatClient 记得配成 qwen-vl-plus
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @KafkaListener(topics = "rag-vision-parsing-topic", groupId = "vision-worker-group")
    public void onMessage(DocumentParseEvent event) {
        log.info("📥 视觉节点收到解析任务: {}", event.getTaskId());

        try {
            // 1. 从 OSS 下载文件
            File tempPdf = downloadFromOss(event.getFileUrl());

            // 2. 提取图片并调用大模型 (就是你之前写的 extractAndAnalyzeImages 逻辑)
            List<Document> imageDocs = extractAndAnalyzeImages(tempPdf, event.getOriginalFileName());

            // 3. 注入权限 Metadata
            imageDocs.forEach(doc -> doc.getMetadata().put("uploaderId", event.getUploaderId()));

            // 4. 存入 Redis 向量库
            vectorStore.add(imageDocs);

            log.info("✅ 任务 [{}] 视觉解析与入库全部完成！", event.getTaskId());

            // (可选) 如果需要，可以在这里通过 Redis 发布订阅 或 Kafka 回写一条“完成”消息给主服务

        } catch (Exception e) {
            log.error("❌ 任务 [{}] 处理失败：", event.getTaskId(), e);
        }
    }

    // 伪代码演示：真正的企业级私有 Bucket 下载
    private File downloadFromOss(String objectName) throws Exception {
        // 1. 初始化 OSS 客户端 (通常从 Spring 容器注入配置)
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        File tempFile = File.createTempFile("rag-download-", ".pdf");

        // 2. 从私有 Bucket 下载文件
        ossClient.getObject(new GetObjectRequest("your-company-bucket", objectName), tempFile);

        ossClient.shutdown();
        return tempFile;
    }
    /**
     * 提取图片并调用视觉模型的专门方法
     */
    private List<Document> extractAndAnalyzeImages(File pdfFile, String realFileName) {
        List<Document> imageDocs = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageNum = 1;
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (COSName xObjectName : resources.getXObjectNames()) {
                    if (resources.isImageXObject(xObjectName)) {
                        // 1. 发现图片，导出为本地临时 PNG
                        PDImageXObject image = (PDImageXObject) resources.getXObject(xObjectName);
                        File tempImg = File.createTempFile("extracted-img-", ".png");
                        ImageIO.write(image.getImage(), "png", tempImg);

                        log.info("📸 在第 {} 页发现图片，正在呼叫视觉大模型 (LLaVA)...", pageNum);

                        // 2. 调用视觉大模型 (强制指定使用 llava 模型)
                        String imageDescription = chatClient.prompt()
                                .options(OllamaOptions.create().withModel("llava"))
                                .user(u -> u.text("你是一个专业的数据分析师。请详细提取并描述这张图片中的所有核心信息：1. 如果是图表，提取具体数值、坐标轴含义和趋势；2. 如果是架构图或流程图，描述节点和流转关系；3. 如果包含文字，请完整摘录。绝不能遗漏关键数据。")
                                        .media(MimeTypeUtils.IMAGE_PNG, new FileSystemResource(tempImg)))
                                .call()
                                .content();

                        log.info("✅ 视觉模型解析完成：{}", imageDescription.substring(0, Math.min(imageDescription.length(), 50)) + "...");

                        // 3. 将图片的文本描述包装成知识切片
                        Document imgDoc = new Document(
                                "[图片解析内容]：\n" + imageDescription,
                                Map.of(
                                        "file_name", realFileName,
                                        "page_number", pageNum,
                                        "content_type", "image_description" // 标记为图片解析数据
                                )
                        );
                        imageDocs.add(imgDoc);

                        // 阅后即焚
                        tempImg.delete();
                    }
                }
                pageNum++;
            }
        } catch (Exception e) {
            log.error("图片提取或解析失败: ", e);
        }
        return imageDocs;
    }
}