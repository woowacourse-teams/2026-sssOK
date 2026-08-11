# syntax=docker/dockerfile:1

# JAR은 CI가 러너에서 네이티브로 미리 빌드해서 build/libs/ 에 놓는다.
# (컴파일을 이미지 안에서 하면 arm64 크로스 빌드 시 QEMU 에뮬레이션으로
#  수 배 느려지기 때문에, 컴파일과 이미지 조립을 분리했다.)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --chown=app:app build/libs/*.jar app.jar

USER app
EXPOSE 8080

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
