# 多阶段构建单个 Java 模块（在仓库根目录执行）
# docker build --platform linux/arm64 -f infra/docker/Dockerfile.java --build-arg MODULE=seckill-user -t seckill-user:0.1.0 .
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /w
COPY pom.xml .
COPY seckill-common seckill-common
COPY seckill-gateway seckill-gateway
COPY seckill-user seckill-user
COPY seckill-activity seckill-activity
COPY seckill-core seckill-core
COPY seckill-order seckill-order
ARG MODULE
RUN mvn -pl "${MODULE}" -am -DskipTests package -q

FROM eclipse-temurin:17-jre-jammy
ARG MODULE
WORKDIR /app
COPY --from=build /w/${MODULE}/target/${MODULE}-*.jar /app/app.jar
ENV JAVA_OPTS="-Xms128m -Xmx256m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
