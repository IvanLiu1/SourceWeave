# SourceWeave

SourceWeave 是一个基于 RAG（检索增强生成）的企业知识管理系统。它提供文档上传与解析、混合检索、带引用的流式问答、持久化会话、多租户隔离和用量管理。

## 主要能力

- 文档上传、解析、分块、向量化与异步索引
- Elasticsearch 关键词与向量混合检索，支持可配置 rerank
- WebSocket 流式聊天、断线重连和持久化历史记录
- 回答引用映射与文档预览
- 基于用户、角色和组织标签的多租户访问控制
- 可配置的 LLM、Embedding Provider 与运行时限额
- 可复现的 RAG 回归评测

WebSocket 连接使用 Redis 中的短时一次性票据（默认 30 秒，握手时原子消费），每次重连都会重新换票，访问 JWT 不会进入 WebSocket URL。应用、开发代理和 Nginx 日志中的 `/chat/{ticket}` 统一记录为 `/chat/{redacted}`。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4、Spring Security、Spring Data JPA、WebSocket |
| 前端 | Vue 3、TypeScript、Vite、Naive UI、Pinia、UnoCSS |
| 数据 | MySQL、Redis、Elasticsearch |
| 异步与存储 | Kafka、MinIO |
| 文档与 AI | Apache Tika、可配置的 LLM / Embedding / Rerank API |

## 系统链路

```text
浏览器
  ├─ REST / WebSocket ─> Spring Boot
  └─ 文件预览 ─────────> MinIO

Spring Boot
  ├─ MySQL          用户、文档元数据、持久化会话
  ├─ Redis          缓存、短期会话、令牌和 WebSocket 票据
  ├─ Kafka          异步文件处理
  ├─ Elasticsearch  文本与向量检索
  └─ MinIO          原始文档和解析产物
```

## 本地开发

### 前置条件

- Java 17
- Maven 3.8+
- Node.js 18.20+
- pnpm 8.7+
- MySQL 8、Redis 7、Kafka、Elasticsearch 8.10 和 MinIO

基础设施可以使用本机服务，也可以使用 `docs/docker-compose.yaml`。实际地址和凭据必须与根目录 `.env` 保持一致。

### 1. 准备配置

```bash
cp .env.example .env
```

至少检查以下配置：

- MySQL、Redis、Kafka、Elasticsearch 和 MinIO 连接信息
- `JWT_SECRET_KEY`，必须是 Base64 编码且解码后不少于 32 字节
- LLM、Embedding Provider 的地址、模型和 API Key
- 首次创建管理员时临时启用 `ADMIN_BOOTSTRAP_ENABLED`

后端会自动读取根目录 `.env`。前端配置位于 `frontend/.env*`。

### 2. 启动依赖

如使用仓库内的本地 Docker 配置：

```bash
docker compose -f docs/docker-compose.yaml up -d
```

该配置包含 MySQL、Redis、Kafka、Elasticsearch 和 MinIO。请根据其中的端口与密码同步调整 `.env`。

### 3. 启动后端

推荐在 IDE 中运行：

```text
src/main/java/com/ivanliu/ragproject/RagProjectApplication.java
```

也可以使用：

```bash
mvn spring-boot:run
```

后端默认监听 `http://localhost:8081`。开发中修改 Java 代码后，通常执行以下命令触发热加载，无需手动重启：

```bash
mvn -q -DskipTests compile
```

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认访问 `http://localhost:9527`，并通过 Vite 代理访问后端。

## 验证

```bash
# 后端测试
mvn test

# 前端类型检查
cd frontend && pnpm typecheck

# 指定文件 lint
cd frontend && pnpm exec eslint <file>
```

涉及聊天或历史记录的改动，应同时检查浏览器网络响应、后端日志以及 MySQL/Redis 中的实际数据。

## Docker 部署

完整容器部署使用根目录 `docker-compose.yml`：

```bash
cp .env.docker.example .env
docker compose up -d --build
```

详细配置、初始化和排障见 [Docker 全栈部署指南](docs/docker-deploy.md)。

## RAG 评测

仓库包含版本化英文评测集和运行脚本。使用方法、指标定义与基线见 [RAG Evaluation v1](docs/rag-evaluation-v1.md)。

`evaluation/runs/` 是本地运行产物，默认不提交。

## 目录

```text
frontend/             Vue 前端
src/main/java/        Spring Boot 后端
src/main/resources/   应用配置与 Elasticsearch mapping
src/test/             后端测试
evaluation/           版本化评测数据集
scripts/              评测和冒烟测试脚本
docs/                 部署、评测与运维文档
```

## License

[Apache License 2.0](LICENSE)
