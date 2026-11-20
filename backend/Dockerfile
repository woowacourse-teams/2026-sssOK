# syntax=docker/dockerfile:1

# JNI 연결 계층만 빌드한다. 컴파일러와 헤더는 최종 이미지에 포함하지 않는다.
FROM eclipse-temurin:21-jdk-alpine AS native-build
RUN apk add --no-cache build-base pkgconf vips-dev
COPY src/main/native/sssok_vips.c /native/sssok_vips.c
RUN cc -O2 -fPIC -shared -Wall -Wextra -Werror \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    /native/sssok_vips.c -o /native/libsssok_vips.so $(pkg-config --cflags --libs vips) -lm

FROM eclipse-temurin:21-jre-alpine AS image-runtime
RUN apk add --no-cache ffmpeg vips vips-heif
COPY --from=native-build /native/libsssok_vips.so /opt/sssok/native/libsssok_vips.so
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/opt/sssok/native"

# 운영과 같은 런타임에서 실제 JNI/Java/영상 계약을 검증한다.
FROM image-runtime AS native-test
COPY build/native-image-test /tests
RUN java -Xmx512m -cp '/tests/classes:/tests/lib/*' com.sssok.benchmark.NativeImageTestRunner

# JAR은 기존 CI 러너에서 빌드한다.
FROM image-runtime

# 배포 파이프라인이 넘기는 버전 식별자. 값이 없으면 unknown 으로 둬서 로컬 빌드도 그대로 된다.
ARG RELEASE_VERSION=unknown
ARG BACKEND_VERSION=unknown
ARG GIT_SHA=unknown
ARG SOURCE_URL=https://github.com/woowacourse-teams/2026-sssOK

# 이미지만 보고도 어떤 빌드인지 알 수 있게 표준 라벨로 남긴다 (docker image inspect).
LABEL org.opencontainers.image.version="${BACKEND_VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      com.sssok.release.version="${RELEASE_VERSION}"

# 런타임에 앱이 읽는 값(APP_RELEASE_VERSION 등)은 이미지에 굽지 않고 compose 가 넘긴다.
# 이미지에 구우면 롤백 때 직전 이미지의 값이 따라오지 않고 새 값이 남아 이력이 어긋난다.

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --chown=app:app build/libs/*.jar app.jar

USER app
EXPOSE 8080

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다.
ENV MEDIA_IMAGE_PROCESSOR=libvips
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
