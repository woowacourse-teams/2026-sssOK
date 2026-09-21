# syntax=docker/dockerfile:1

# JAR은 CI가 러너에서 네이티브로 미리 빌드해서 build/libs/ 에 놓는다.
# (컴파일을 이미지 안에서 하면 arm64 크로스 빌드 시 QEMU 에뮬레이션으로
#  수 배 느려지기 때문에, 컴파일과 이미지 조립을 분리했다.)
FROM eclipse-temurin:21-jre-alpine

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

# 영상 썸네일용. 자바 라이브러리 대신 실행 파일을 쓰는 이유는, 자바 의존성이 늘지 않고
# 깨진 영상에 디코더가 죽어도 별도 프로세스라 서버가 멀쩡하기 때문이다.
# ffprobe 도 같은 패키지에 들어 있다 — 원본 크기와 재생시간을 읽는 데 쓴다.
RUN apk add --no-cache ffmpeg

RUN addgroup -S app && adduser -S app -G app

COPY --chown=app:app build/libs/*.jar app.jar

USER app
EXPOSE 8080

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
