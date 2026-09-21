package com.sssok.infrastructure.config;

import com.sssok.common.version.VersionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(ReleaseProperties.class)
public class VersionConfig {

    @Bean
    public VersionInfo versionInfo(ObjectProvider<BuildProperties> buildProperties, ReleaseProperties release) {
        VersionInfo versionInfo = new VersionInfo(
            release.version(), resolveBackendVersion(buildProperties, release), release.gitSha());
        log.info("버전 정보 — 통합 릴리스: {}, 백엔드: {}, 커밋: {}",
            versionInfo.releaseVersion(), versionInfo.backendVersion(), versionInfo.gitSha());
        return versionInfo;
    }

    // 백엔드 버전의 1순위는 JAR 안의 build-info 다.
    // build-info.properties 는 bootJar 로 만든 산출물에만 들어가므로, IDE 에서 클래스를 바로 실행하거나
    // ./gradlew bootRun 으로 띄우면 BuildProperties 빈 자체가 없다. 그래서 주입 대신 ObjectProvider 로
    // 받아 부재를 허용하고, 없으면 파이프라인이 넘긴 환경변수를 쓴다. 둘 다 없으면 unknown.
    private String resolveBackendVersion(ObjectProvider<BuildProperties> buildProperties, ReleaseProperties release) {
        BuildProperties buildInfo = buildProperties.getIfAvailable();
        if (buildInfo != null) {
            return buildInfo.getVersion();
        }
        String injected = release.backendVersion();
        return injected == null || injected.isBlank() ? VersionInfo.UNKNOWN : injected;
    }
}
