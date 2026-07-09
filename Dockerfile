# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先复制 pom.xml 预下载依赖，充分利用 Docker 层缓存
COPY pom.xml .
RUN mvn -B dependency:go-offline || true

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd -r ragproject && useradd -r -g ragproject ragproject

COPY --from=build /app/target/ragproject-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-Xms512m -Xmx1g"

USER ragproject
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/localhost/${SERVER_PORT:-8081}' || exit 1

ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar app.jar"]
