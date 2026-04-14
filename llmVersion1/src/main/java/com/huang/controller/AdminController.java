package com.huang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPooled; // 引入原生的 Jedis
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/13 13:52
 **/
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminController(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @GetMapping("/docs")
    public List<Map<String, String>> getAllDocuments() {
        List<Map<String, String>> resultList = new ArrayList<>();
        try {
            Set<String> keys = jedis.keys("doc*");
            if (keys == null || keys.isEmpty()) {
                return resultList;
            }
            for (String key : keys) {
                Object rawJsonObj = jedis.jsonGet(key);
                if (rawJsonObj != null) {
                    JsonNode rootNode;
                    // 兼容 Jackson 解析，防止报错
                    if (rawJsonObj instanceof String) {
                        rootNode = objectMapper.readTree((String) rawJsonObj);
                    } else {
                        rootNode = objectMapper.valueToTree(rawJsonObj);
                    }
                    // 提取原始数据
                    String rawContent = rootNode.path("content").asText("无文本内容");
                    String fileName = rootNode.path("file_name").asText("未知文件");
                    String pageNumber = rootNode.path("page_number").asText("0");
                    // 核心数据清洗：去除大量连续空格和换行符，让内容美观紧凑
                    String cleanContent = rawContent.replaceAll("\\s+", " ").trim();
                    Map<String, String> docMap = new HashMap<>();
                    docMap.put("id", key.substring(0, Math.min(key.length(), 15)) + "...");
                    docMap.put("fileName", fileName);
                    docMap.put("page", pageNumber);
                    // 摘要截断以适应表格
                    docMap.put("contentSummary", cleanContent.length() > 50 ? cleanContent.substring(0, 50) + "..." : cleanContent);
                    docMap.put("fullContent", cleanContent);
                    resultList.add(docMap);
                }
            }
        } catch (Exception e) {
            log.error("❌ 查询知识库后台数据失败：", e);
        }
        return resultList;
    }
}