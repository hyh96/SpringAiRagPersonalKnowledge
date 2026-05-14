package com.huang.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/5/8 11:46
 **/
@Slf4j
@Service
@RequiredArgsConstructor
//读取pdf 文本模式
public class PdfVectorService {

    // Spring AI 会自动帮你注入我们在 application.yml 里配置好的 Redis 向量库
    private final VectorStore vectorStore;

    /**
     * 核心逻辑：解析PDF -> 切片 -> 向量化存入 Redis
     */
    public void processAndStore(String filePath, String fileMd5, String fileName, String uploaderId) {
        log.info("开始处理 PDF 文件向量化任务，文件路径: {}", filePath);
        File pdfFile = new File(filePath);

        if (!pdfFile.exists()) {
            log.error("❌ 文件不存在，处理失败: {}", filePath);
            return;
        }

        // 使用 PDFBox 读取文件内容 (3.x 版本使用 Loader.loadPDF)
        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            // 1. 提取整篇文档的纯文本
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            log.info("📄 PDF 文本提取完成，总字数: {}", text.length());

            if (text.trim().isEmpty()) {
                log.warn("⚠️ 提取到的文本为空，可能是纯扫描件图片，暂不处理。");
                return;
            }

            // 2. 将整段文字包装成 Spring AI 的 Document 对象，并打上“元数据(Metadata)”标签
            // 以后我们要按 uploaderId 隔离数据，或者按 fileMd5 删除旧向量，全靠这几个标签！
            Document aiDocument = new Document(text, Map.of(
                    "fileMd5", fileMd5,
                    "fileName", fileName,
                    "uploaderId", uploaderId
            ));

            // 3. 文本切片 (Chunking) - 大模型记忆力有限，必须把长文切成短片
            TokenTextSplitter splitter = new TokenTextSplitter(
                    800,   // 每个切片的最大 token 数 (大约五六百个汉字)
                    300,   // 切片之间的重叠字数 (防止把一句话生硬地切断)
                    5,     // 最小切片长度
                    10000,
                    true
            );
            List<Document> splitDocuments = splitter.apply(List.of(aiDocument));
            log.info("✂️ 文本切片完成，共切出 {} 个片段 (Chunks)", splitDocuments.size());

            // 4. 最激动人心的一步：直接塞进向量数据库！
            // Spring AI 底层会自动调用百炼的 text-embedding-v3 模型生成向量，然后写入 Redis
            vectorStore.add(splitDocuments);
            log.info("✅ 完美收工！文件 [{}] 的所有切片已全部转化为高维向量并存入 Redis！", fileName);

        } catch (IOException e) {
            log.error("❌ PDF 解析发生异常: ", e);
        } catch (Exception e) {
            log.error("❌ 向量化或存入 Redis 时发生异常: ", e);
        }
    }
}