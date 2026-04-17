package com.huang.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/17 14:46
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentParseEvent implements Serializable {
    private String taskId;           // 任务追踪 ID
    private String originalFileName; // 原文件名
    private String fileUrl;          // 文件在云端 OSS 或共享存储的下载地址
    private String uploaderId;       // 谁上传的（用于权限隔离）
}