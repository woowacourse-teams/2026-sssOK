# 비교 실험 전용. backend 디렉터리를 build context로 사용한다.
FROM eclipse-temurin:21-jdk-alpine AS jni21
RUN apk add --no-cache build-base pkgconf vips-dev vips-tools ffmpeg
COPY src/main/native/sssok_vips.c /native/sssok_vips.c
RUN cc -O2 -fPIC -shared -Wall -Wextra -Werror \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    /native/sssok_vips.c -o /native/libsssok_vips.so $(pkg-config --cflags --libs vips) -lm

# FFM 비교도 동일한 OS·libvips·JNI 바이너리를 사용하고 JDK만 교체한다.
FROM eclipse-temurin:25-jdk-alpine AS jdk25
FROM jni21 AS ffm25
RUN rm -rf /opt/java/openjdk
COPY --from=jdk25 /opt/java/openjdk /opt/java/openjdk

# 기존 Java 21 / CLI 실험의 기본 빌드 대상도 유지한다.
FROM jni21 AS comparison
