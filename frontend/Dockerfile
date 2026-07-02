# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /app

# lockfileVersion 9.0，需要 pnpm 9+
RUN corepack enable && corepack prepare pnpm@latest-10 --activate

# pnpm workspace：先复制清单文件安装依赖，利用层缓存
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages ./packages
COPY build ./build
RUN pnpm install --frozen-lockfile

COPY . .
# 使用 prod 模式构建（API 走同源 /api/v1，WS 走同源 /proxy-ws）
RUN pnpm build

# ---- 运行阶段 ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO /dev/null http://localhost/ || exit 1
