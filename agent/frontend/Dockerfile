# 前端构建阶段
FROM node:20-alpine AS build
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build

# 运行阶段 - 使用 nginx 托管静态文件
FROM nginx:alpine
# 复制构建产物到 nginx 静态文件目录
COPY --from=build /app/dist /usr/share/nginx/html
# 仅替换后端端口；保留Nginx自身的$uri等变量，缺少端口时启动应失败。
ENV NGINX_ENVSUBST_FILTER=^ZHKU_CONTROLLED_BACKEND_PORT$
COPY nginx.conf /etc/nginx/templates/default.conf.template
# 暴露端口
EXPOSE 80
# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
