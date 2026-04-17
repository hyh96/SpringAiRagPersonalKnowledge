package com.huang.controller;

/**
 * @Author: huang
 * @Description: 图片识别
 * @DateTime: 2026/4/3 9:43
 **/
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@RestController
public class PicChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // Spring AI 推荐使用 ChatClient.Builder 来构建对话客户端
    public PicChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @GetMapping("/api/ask")
    public String askCompanyQuestion(@RequestParam String question) {
        // 步骤 1：检索 (Retrieval)
        // 去向量库里搜索与 question 最相关的 3 个文档块
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(3)
        );

        // 把这 3 个文档块的内容拼接成一个大字符串
        String documentContext = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // 步骤 2：生成 (Generation)
        // 设计系统提示词，给 AI 戴上“紧箍咒”
        String systemPrompt = """
            你是一个公司内部知识库助手。
            请严格基于以下提供的<参考资料>来回答用户的问题。
            如果在<参考资料>中找不到答案，请直接回答“根据内部资料，我无法回答该问题”，绝不允许胡编乱造！
            
            <参考资料>
            {context}
            </参考资料>
            """;

        // 发送给本地大模型并获取回答
        return chatClient.prompt()
                .system(sys -> sys.text(systemPrompt).param("context", documentContext)) // 动态注入刚才搜到的资料
                .user(question) // 用户的实际问题
                .call()
                .content();
    }

    // ========== 改造点 1：设置 produces 属性，告诉浏览器这是服务器推送事件流 ==========
    @GetMapping(value = "/api/ask/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> askCompanyQuestionStream(@RequestParam String question) { // 改造点 2：返回值从 String 变成 Flux<String>

        // 步骤 1：检索 (Retrieval) - 保持不变
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(3)
        );

        String documentContext = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // 步骤 2：设计提示词 - 保持不变
        String systemPrompt = """
            你是一个公司内部知识库助手。
            请严格基于以下提供的<参考资料>来回答用户的问题。
            如果在<参考资料>中找不到答案，请直接回答“根据内部资料，我无法回答该问题”，绝不允许胡编乱造！
            
            <参考资料>
            {context}
            </参考资料>
            """;

        // 步骤 3：调用大模型并返回流
        return chatClient.prompt()
                .system(sys -> sys.text(systemPrompt).param("context", documentContext))
                .user(question)
                .stream()
                .content();
    }

    @GetMapping("/api")
    public void picOcrChatQuestion(@RequestParam String question) {
        // 伪代码演示：如何让 Spring AI 看图
        String imageDescription = chatClient.prompt()
                .user(prompt -> prompt
                .text("你是一个专业的数据分析师。请详细描述这张图片中的内容。如果是图表，请提取出具体的数值和趋势；如果是架构图，请描述各个组件的关系；如果是流程图，请写出具体步骤。绝不能遗漏关键信息。")
                .media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("temp_image.png")))
                .call()
                .content();
        log.info("AI 对图片的描述是：{}", imageDescription);
    }

}