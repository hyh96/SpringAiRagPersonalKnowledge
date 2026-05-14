package com.huang.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
/**
 * 文档解析事件(Kafka消息载体)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentParseEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileMd5;           // 文件的 MD5 (唯一标识，用于防重和向量库关联)
    private String filePath;          // 文件存放在本地磁盘的绝对路径
    private String originalFileName;  // 原始文件名
    private String uploaderId;        // 上传者ID (预留的多租户权限字段)

//    private String taskId;           // 任务唯一ID
//    private String originalFileName; // 原始文件名
//    private String localFilePath;    // 本地暂存的绝对路径 (解决无需OSS的问题)
//    private String uploaderId;       // 上传人(用于权限隔离)
}