package com.huang.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
/**
 * @Author: huang
 * @Description: 处理SSE流式回答
 * @DateTime: 2026/5/6 11:09
 **/
@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatController {
    private final ChatClient chatClient;
    private final VectorStore vectorStore; // 注入向量数据库

    public ChatController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    /**
     * 基于本地知识库的流式问答接口
     */
    @GetMapping(value = "/streamAli", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam("message") String message) {

        log.info("收到用户提问: {}", message);

        // ==========================================
        // 1. 核心步骤：去 Redis 向量库里搜寻相关的文档切片
        // ==========================================
        // withTopK(3) 表示找出跟用户问题最相似的 3 个片段 (每个切片我们之前设定了大概 800 token)
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(message).withTopK(3)
        );

        // 提取文档内容，并拼接成一长段字符串作为“参考资料”
        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        log.info("从知识库检索到 {} 条相关片段作为参考", similarDocuments.size());

        // ==========================================
        // 2. 核心步骤：构建 System Prompt (系统提示词)
        // ==========================================
        // 这是魔法生效的关键！我们告诉大模型它的身份，并强行把参考资料“喂”给它
        String systemPrompt = """
                你是一个专业的企业知识库 AI 助手。
                请严格根据下面提供的【参考资料】来回答用户的问题。
                如果参考资料中没有相关内容，请回答“抱歉，目前的知识库中没有找到相关答案”，绝对不要自己胡编乱造。
                
                【参考资料】：
                %s
                """.formatted(context);

        // ==========================================
        // 3. 核心步骤：向大模型发起流式调用
        // ==========================================
        return this.chatClient.prompt()
                .system(systemPrompt) // 注入我们精心设计的包含私有数据的系统提示词
                .user(message)        // 用户原始的问题
                .stream()             // 开启流式输出
                .content()
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content) // 封装成 SSE 流发给前端
                        .build());
    }
}