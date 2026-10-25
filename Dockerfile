# syntax=docker/dockerfile:1

# ------------------------------------------------------------------
# 一份镜像 = 前端静态资源 + Spring Boot API
# 前端产物在打包阶段落进 backend/src/main/resources/static，
# 运行期由 StaticSiteConfig 负责静态资源与前端路由回落。
# ------------------------------------------------------------------

FROM node:22-alpine AS web
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS api
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
COPY --from=web /build/frontend/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

# 服务端 PDF 导出：无头 Chromium + 中文字体（缺少字体时中文会变成方框）
RUN apk add --no-cache chromium font-noto-cjk font-noto \
  && addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=api /build/target/miniwho-1.0.0.jar app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 8080
ENV SERVER_PORT=8080
# Alpine 的 chromium 固定装在这里；显式指定省掉启动时的路径探测
ENV MINIWHO_PDF_BROWSER_PATH=/usr/bin/chromium
# 无头浏览器访问的就是同一个进程，不再需要单独的前端容器
ENV MINIWHO_PDF_RENDER_BASE_URL=http://127.0.0.1:8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
