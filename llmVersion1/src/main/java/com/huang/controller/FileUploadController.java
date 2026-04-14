package com.huang.controller;

/**
 * @Author: huang
 * @Description: TODO
 * @DateTime: 2026/4/3 9:43
 **/
import com.huang.loader.FileUploadDataLoaderService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/doc")
public class FileUploadController {

    private final FileUploadDataLoaderService dataLoaderService;

    public FileUploadController(FileUploadDataLoaderService dataLoaderService) {
        this.dataLoaderService = dataLoaderService;
    }

    // 提供给前端的上传接口
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "上传失败：请选择一个文件！";
        }
        return dataLoaderService.uploadAndParse(file);
    }
}