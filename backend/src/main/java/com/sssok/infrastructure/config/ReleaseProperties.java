package com.sssok.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 통합 릴리스 정보. 배포 파이프라인이 compose 를 통해 컨테이너 환경변수로 넣어준다.
// backendVersion 은 JAR 의 build-info 가 없을 때만 쓰는 보조 값이다 (VersionConfig 참고).
@ConfigurationProperties(prefix = "release")
public record ReleaseProperties(String version, String backendVersion, String gitSha) {
}
