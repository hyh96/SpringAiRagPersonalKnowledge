package com.huang.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.document.Document;
import redis.clients.jedis.JedisPooled;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * @Author: huang
 * @Description: 页面上传文件解析存进向量数据库
 * @DateTime: 2026/4/3 15:59
 **/
@Service
@Slf4j
public class FileUploadDataLoaderService {

    private final VectorStore vectorStore;

    //通过文件内容算出一个独一无二的“数字指纹，确保存入向量库的内容不会重复
    private final JedisPooled jedis; // 引入 Jedis 用于操作指纹记录

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileUploadDataLoaderService(VectorStore vectorStore,JedisPooled jedis) {
        this.vectorStore = vectorStore;
        this.jedis=jedis;
    }

    public String uploadAndParse(MultipartFile file) {
        String realFileName = file.getOriginalFilename();
        if (realFileName == null) realFileName = "未知上传文件.pdf";
        log.info("====== 开始处理前端上传的文件：{} ======", realFileName);
        try {
            // 1. 计算文件数字指纹 (MD5)
            String fileMd5;
            try (InputStream is = file.getInputStream()) {
                fileMd5 = DigestUtils.md5DigestAsHex(is);
            }
            log.info("🔍 文件 MD5 指纹计算完成: {}", fileMd5);
            String hashKey = "file_fingerprint:" + fileMd5;
            String fileNameKey = "file_name_to_md5:" + realFileName;
            // 2. 拦截完全重复的文件
            if (jedis.exists(hashKey)) {
                String existFileName = jedis.get(hashKey);
                log.warn("🚫 拒绝入库：检测到内容完全相同的文件 [{}]", existFileName);
                return "上传被拦截：系统内已存在内容完全相同的文件，无需重复录入！";
            }
            // 3. 同名文件更新逻辑 (删除旧版向量数据)
            if (jedis.exists(fileNameKey)) {
                String oldMd5 = jedis.get(fileNameKey);
                jedis.del("file_fingerprint:" + oldMd5); // 清理旧指纹
                deleteOldDocument(realFileName);         // 清理旧切片
                log.info("♻️ 旧版本已清理，准备存入新版本。");
            }
            // 4. 解析文件并切片
            File tempFile = File.createTempFile("rag-upload-", ".pdf");
            file.transferTo(tempFile);
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(new FileSystemResource(tempFile));
            List<Document> documents = pdfReader.get();
            TokenTextSplitter textSplitter = new TokenTextSplitter(1000, 200, 5, 10000, true);
            List<Document> splitDocuments = textSplitter.apply(documents);
            // 5. 修复文件名，防止使用临时文件名入库
            for (Document doc : splitDocuments) {
                doc.getMetadata().put("file_name", realFileName);
            }
            // 6. 调用大模型向量化并存入 Redis
            log.info("⏳ 正在调用大模型生成向量并存入知识库...");
            vectorStore.add(splitDocuments);
            tempFile.delete();
            // 7. 登记新文件的指纹和名称映射
            jedis.set(hashKey, realFileName);
            jedis.set(fileNameKey, fileMd5);
            return "入库成功！文件 [" + realFileName + "] 共切分为 " + splitDocuments.size() + " 个知识片段。";
        } catch (Exception e) {
            log.error("❌ 文件处理失败：", e);
            return "入库失败：" + e.getMessage();
        }
    }

    private void deleteOldDocument(String fileName) {
        log.info("🧹 检测到同名文件更新，正在清理旧版数据: {}", fileName);
        // 1. 在 Redis 中寻找所有属于该文件的切片 Key
        Set<String> keys = jedis.keys("doc*");
        if (keys == null) return;
        for (String key : keys) {
            Object rawJsonObj = jedis.jsonGet(key);
            if (rawJsonObj != null) {
                try {
                    JsonNode rootNode = objectMapper.readTree(rawJsonObj.toString());
                    // 如果这个切片的来源文件名和当前上传的文件名一致，直接干掉
                    if (fileName.equals(rootNode.path("file_name").asText())) {
                        jedis.del(key);
                    }
                } catch (Exception e) {
                    log.error("清理旧切片失败: {}", key);
                }
            }
        }
    }
}
