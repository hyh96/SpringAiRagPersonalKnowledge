package com.huang.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.JedisPooled;
import org.apache.pdfbox.Loader;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/14 16:58
 **/
@Slf4j
@Service
public class PicDataLoaderService {

    private final VectorStore vectorStore;
    private final JedisPooled jedis;
    private final ChatClient chatClient; // 引入 ChatClient 用于调用视觉大模型
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PicDataLoaderService(VectorStore vectorStore, JedisPooled jedis, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.jedis = jedis;
        this.chatClient = chatClientBuilder.build();
    }

    public String uploadAndParse(MultipartFile file) {
        String realFileName = file.getOriginalFilename();
        if (realFileName == null) realFileName = "未知上传文件.pdf";

        log.info("====== 开始处理前端上传的文件：{} ======", realFileName);

        try {
            // 1. 计算 MD5 防重复与同名覆盖逻辑 (保持不变)
            String fileMd5;
            try (InputStream is = file.getInputStream()) {
                fileMd5 = DigestUtils.md5DigestAsHex(is);
            }
            String hashKey = "file_fingerprint:" + fileMd5;
            String fileNameKey = "file_name_to_md5:" + realFileName;

            if (jedis.exists(hashKey)) {
                return "上传被拦截：系统内已存在内容完全相同的文件，无需重复录入！";
            }
            if (jedis.exists(fileNameKey)) {
                String oldMd5 = jedis.get(fileNameKey);
                jedis.del("file_fingerprint:" + oldMd5);
                deleteOldDocument(realFileName);
            }

            File tempFile = File.createTempFile("rag-upload-", ".pdf");
            file.transferTo(tempFile);

            // ================= 核心升级：图文双轨解析 =================
            List<Document> finalDocuments = new ArrayList<>();

            // 轨道 A：提取纯文本并切片
            log.info("⏳ [轨道 A] 正在解析 PDF 纯文本...");
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(new FileSystemResource(tempFile));
            List<Document> textDocuments = pdfReader.get();
            TokenTextSplitter textSplitter = new TokenTextSplitter(1000, 200, 5, 10000, true);
            List<Document> splitTextDocs = textSplitter.apply(textDocuments);
            for (Document doc : splitTextDocs) {
                doc.getMetadata().put("file_name", realFileName);
                doc.getMetadata().put("content_type", "text"); // 标记为纯文本
                finalDocuments.add(doc);
            }

            // 轨道 B：提取图片并调用视觉模型识别
            log.info("⏳ [轨道 B] 正在扫描 PDF 中的图片并调用大模型识别...");
            List<Document> imageDocuments = extractAndAnalyzeImages(tempFile, realFileName);
            finalDocuments.addAll(imageDocuments);
            // ========================================================

            // 最终入库
            log.info("⏳ 正在调用大模型生成向量并存入知识库 (共 {} 个切片，含 {} 张图片解析)...", finalDocuments.size(), imageDocuments.size());
            vectorStore.add(finalDocuments);
            tempFile.delete();

            jedis.set(hashKey, realFileName);
            jedis.set(fileNameKey, fileMd5);

            return "入库成功！文件 [" + realFileName + "] 共生成 " + finalDocuments.size() + " 个知识片段（包含图表识别）。";

        } catch (Exception e) {
            log.error("❌ 文件处理失败：", e);
            return "入库失败：" + e.getMessage();
        }
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

    private void deleteOldDocument(String fileName) {
        log.info("🧹 正在清理旧版数据...");
        Set<String> keys = jedis.keys("doc*");
        if (keys == null || keys.isEmpty()) return;
        for (String key : keys) {
            try {
                Object rawJsonObj = jedis.jsonGet(key);
                if (rawJsonObj != null) {
                    JsonNode rootNode = (rawJsonObj instanceof String) ?
                            objectMapper.readTree((String) rawJsonObj) : objectMapper.valueToTree(rawJsonObj);
                    if (fileName.equals(rootNode.path("file_name").asText())) {
                        jedis.del(key);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
