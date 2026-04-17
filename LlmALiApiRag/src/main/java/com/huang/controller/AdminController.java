package com.huang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPooled;

import java.util.*;

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

    public AdminController(JedisPooled jedis) { this.jedis = jedis; }

    @GetMapping("/docs")
    public List<Map<String, String>> getAllDocuments() {
        List<Map<String, String>> resultList = new ArrayList<>();
        try {
            Set<String> keys = jedis.keys("doc*");
            if (keys == null || keys.isEmpty()) return resultList;
            for (String key : keys) {
                Object rawJsonObj = jedis.jsonGet(key);
                if (rawJsonObj != null) {
                    JsonNode rootNode = (rawJsonObj instanceof String) ? objectMapper.readTree((String) rawJsonObj) : objectMapper.valueToTree(rawJsonObj);
                    String rawContent = rootNode.path("content").asText("");
                    String fileName = rootNode.path("file_name").asText("未知文件");
                    String pageNumber = rootNode.path("page_number").asText("0");
                    String contentType = rootNode.path("content_type").asText("text");

                    // 如果是图片描述，前端展示给个特殊的 Tag 标识
                    String typeTag = "image_description".equals(contentType) ? "🖼️ 图片/图表解析" : "📄 纯文本";

                    String cleanContent = rawContent.replaceAll("\\s+", " ").trim();
                    Map<String, String> docMap = new HashMap<>();
                    docMap.put("id", key.substring(0, Math.min(key.length(), 15)) + "...");
                    docMap.put("fileName", fileName);
                    docMap.put("page", pageNumber);
                    docMap.put("typeTag", typeTag);
                    docMap.put("fullContent", cleanContent);
                    resultList.add(docMap);
                }
            }
        } catch (Exception e) { log.error("查询失败：", e); }
        return resultList;
    }
}