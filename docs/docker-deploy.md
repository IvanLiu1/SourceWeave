# Docker 全栈部署指南

一条 `docker compose` 命令拉起完整系统：MySQL、Redis、Kafka、Elasticsearch（含 IK 分词）、MinIO、后端（Spring Boot）、前端（nginx）。

> 仅需基础设施做本地开发（IDE 里跑后端、`pnpm dev` 跑前端）请继续使用 `docs/docker-compose.yaml`。

## 前置条件

- Docker 20.10+ 与 Docker Compose v2（`docker compose version` 可验证）
- 服务器建议至少 4GB 内存（Elasticsearch 默认占 1~2GB）
- 首次构建需要外网（拉镜像、Maven/pnpm 依赖、ES 的 IK 分词插件）

## 部署步骤

```bash
# 1. 准备配置
cp .env.docker.example .env
vim .env   # 填写所有 change-me 密码、JWT_SECRET_KEY、AI API Key

# 2. 构建并启动全部服务
docker compose up -d --build

# 3. 观察启动进度（基础设施健康后才会启动后端）
docker compose ps
docker compose logs -f backend
```

启动完成后访问 `http://<服务器IP>:8080`（端口由 `FRONTEND_PORT` 控制）。

### 首次初始化管理员

1. `.env` 中设置 `ADMIN_BOOTSTRAP_ENABLED=true` 及管理员用户名/密码
2. `docker compose up -d backend` 重建后端容器
3. 登录验证成功后，改回 `ADMIN_BOOTSTRAP_ENABLED=false`，再次 `docker compose up -d backend`

## 架构说明

```
浏览器 ──> frontend (nginx :8080)
              ├── /            静态资源 (Vue dist)
              ├── /api/        ──> backend:8081 (REST)
              └── /proxy-ws/   ──> backend:8081 (WebSocket 聊天)
浏览器 ──> minio :19000 (文件预览/下载，MINIO_PUBLIC_URL)

backend ──> mysql:3306 / redis:6379 / kafka:9092 / es:9200 / minio:19000（容器内部网络）
```

- 除前端 `8080` 和 MinIO `19000/19001` 外，其余服务端口默认不暴露；后端 `8081` 仅绑定 `127.0.0.1`（供宿主机 nginx 反代场景）。调试需要时在 `docker-compose.yml` 中取消对应 `ports` 注释。
- Kafka 配置了双监听器：容器间走 `kafka:9092`，宿主机调试可打开 `127.0.0.1:9094`。
- Kafka topic 由后端启动时自动创建，无需手工建 topic。
- 数据均持久化在命名卷中：`mysql-data`、`redis-data`、`kafka-data`、`es-data`、`minio-data`。

## 常用运维命令

```bash
# 更新代码后重新构建发布（仅后端）
docker compose build backend && docker compose up -d backend

# 仅前端
docker compose build frontend && docker compose up -d frontend

# 查看日志
docker compose logs -f backend
docker compose logs --tail=100 es

# 停止 / 启动
docker compose stop
docker compose start

# 彻底删除（含数据卷，慎用！）
docker compose down -v
```

## 生产环境注意事项

1. **域名 + HTTPS**：建议在宿主机（或云负载均衡）再加一层 nginx/caddy 做 TLS 终结，反代到 `frontend` 的 8080 端口；`MINIO_PUBLIC_URL` 与 `SECURITY_ALLOWED_ORIGINS` 同步改为实际域名。
2. **密钥管理**：`.env` 含全部密码，权限设为 `600`，不要提交进 git（已在 `.gitignore`）。
3. **备份**：定期备份 `mysql-data` 与 `minio-data` 卷。
4. **资源调优**：内存紧张时下调 `ES_JAVA_OPTS`/`JAVA_OPTS`；文件解析量大时可上调后端内存。
5. **镜像加速**：国内服务器建议配置 Docker registry mirror，Maven/pnpm 依赖可通过构建参数或代理加速。

## 故障排查

| 现象 | 排查方向 |
| --- | --- |
| backend 一直重启 | `docker compose logs backend`,常见为 `.env` 缺 `JWT_SECRET_KEY` 或数据库密码错误 |
| es 起不来 / 被 OOM | 下调 `ES_JAVA_OPTS`,确认 `ES_MEMORY_LIMIT` ≥ 堆内存两倍左右;首次启动要下载 IK 插件需外网 |
| 文件上传后搜索不到 | 看 `docker compose logs kafka backend`,确认 vectorization 消费链路;检查 EMBEDDING_API_KEY |
| 文件预览 404 | `MINIO_PUBLIC_URL` 必须是浏览器可达的地址(服务器公网 IP 或域名 + 19000) |
| 聊天连不上(WS 断开) | 确认走的是 `/proxy-ws/` 路径;若外层还有 nginx,需同样配置 `Upgrade/Connection` 头 |
