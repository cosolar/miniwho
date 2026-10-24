FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

# 服务端 PDF 导出：无头 Chromium + 中文字体（缺少字体时中文会变成方框）
RUN apk add --no-cache chromium font-noto-cjk font-noto \
  && addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /build/target/miniwho-1.0.0.jar app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 8080
ENV SERVER_PORT=8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
