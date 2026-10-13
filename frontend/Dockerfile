# =====================================================
# Frontend Dockerfile (Vue3 + Vite + Nginx)
# 多阶段构建：阶段 1 用 Node 编译出静态文件，阶段 2 用 Nginx 托管
# =====================================================

# ---------- 阶段 1：构建 ----------
FROM node:20-alpine AS builder

WORKDIR /workspace

# 优先拷贝 package 文件，安装依赖（命中 Docker 缓存）
COPY package*.json ./
RUN npm install --no-audit --no-fund

# 再拷贝源码并构建
COPY . .

# 生产构建：使用 .env.production 中的 VITE_API_BASE_URL=/api
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

# 时区
ENV TZ=Asia/Shanghai

# 删除默认站点配置，使用我们的反代 + SPA 路由配置
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf

# 拷贝构建产物
COPY --from=builder /workspace/dist /usr/share/nginx/html

EXPOSE 80

# 容器健康检查：访问首页，期望 200
HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
