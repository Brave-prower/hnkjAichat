# =====================================================
# Backend Dockerfile (Spring Boot 3 + JDK 17)
# 多阶段构建：第一阶段用 Maven 编译打包，第二阶段用精简 JRE 运行
# =====================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# 先拷贝 pom.xml 并下载依赖，利用 Docker 缓存：源码没变时依赖层不会重新拉
COPY pom.xml .
RUN mvn -B -e -ntp dependency:go-offline

# 再拷贝源码并打包（跳过测试，加快构建；如需测试可去掉 -DskipTests）
COPY src ./src
RUN mvn -B -e -ntp -DskipTests package

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-jammy

# 时区与字符集
ENV TZ=Asia/Shanghai \
    LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8

WORKDIR /app

# 从构建阶段拷贝可执行 jar
COPY --from=builder /workspace/target/*.jar /app/app.jar

# 上传文件持久化目录（与 docker-compose 中的 volume 对应）
RUN mkdir -p /app/uploads

# Spring Boot 3 默认暴露 8080；显式声明便于阅读
EXPOSE 8080

# JVM 容器感知参数 + 默认 local profile（普通表余弦检索，任何 PG 都能跑）；
# 部署 Railway 时通过环境变量 SPRING_PROFILES_ACTIVE=railway 切回 pgvector
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=local

# 容器健康检查：命中 Spring Boot Actuator 的健康端点
# 注意 1：context-path=/api，所以应用内路径是 /api/actuator/health
# 注意 2：eclipse-temurin:jre-jammy 镜像不含 wget/curl，这里显式安装 wget，
#         否则 HEALTHCHECK 永远失败、容器一直显示 unhealthy
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/api/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
