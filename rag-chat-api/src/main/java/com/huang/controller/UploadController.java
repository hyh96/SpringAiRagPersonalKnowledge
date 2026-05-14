package com.huang.controller;
import com.huang.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;
/**
 * @Author: huang
 * @Description: 处理上传并发送kafka
 * @DateTime: 2026/4/17 14:50
 **/
@RestController
@RequestMapping("/api/file")
@CrossOrigin // 允许跨域请求
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploadService;
    /**
     * 文件上传接口
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploaderId", defaultValue = "admin_user") String uploaderId) {

        Map<String, Object> response = new HashMap<>();
        try {
            // 简单拦截一下：只允许上传 PDF 文件
            if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                response.put("code", 400);
                response.put("message", "请上传有效的 PDF 文件哦");
                return ResponseEntity.badRequest().body(response);
            }

            // 调用 Service 保存文件并抛给 Kafka
            String fileMd5 = uploadService.uploadAndSendTask(file, uploaderId);

            // 返回成功信息给前端
            response.put("code", 200);
            response.put("message", "文件上传成功，已加入后台智能解析队列！");
            response.put("fileId", fileMd5); // 返回 fileId 供前端后续查询进度

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("code", 500);
            response.put("message", "文件处理失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}