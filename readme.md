SpringAIRag (父工程 / 统一管理依赖版本)
├── pom.xml
│
├── rag-common (公共模块 / 存放 DTO、事件实体、全局常量)
│   ├── pom.xml
│   └── src/main/java/com/huang/common/event/DocumentParseEvent.java
│
├── rag-chat-api (API 门户微服务 / 端口 8083 / 面向用户)
│   ├── pom.xml
│   ├── src/main/resources/application.yml
│   └── src/main/java/com/huang/chat/
│       ├── ChatApiApplication.java
│       ├── controller/UploadController.java (处理上传并发送 Kafka)
│       └── controller/ChatController.java (处理 SSE 流式问答)
│
└── rag-vision-worker (后台视觉微服务 / 端口 8082 / 面向大模型)
├── pom.xml
├── src/main/resources/application.yml
└── src/main/java/com/huang/vision/
├── VisionWorkerApplication.java
└── listener/VisionWorkerListener.java (监听 Kafka 并调用云端视觉模型)