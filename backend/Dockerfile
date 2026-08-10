# syntax=docker/dockerfile:1

# ---------- 빌드 스테이지 ----------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 캐시 적중률을 높인다.
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew clean bootJar --no-daemon -x test \
    && mv build/libs/*.jar app.jar

# ---------- 실행 스테이지 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder --chown=app:app /workspace/app.jar app.jar

USER app
EXPOSE 8080

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
