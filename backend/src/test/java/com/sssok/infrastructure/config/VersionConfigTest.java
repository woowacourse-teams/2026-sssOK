package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.common.version.VersionInfo;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VersionConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(VersionConfig.class);

    @Test
    void 빌드_정보가_있으면_백엔드_버전을_거기서_읽는다() {
        // 산출물 자체가 들고 있는 값이므로 주입된 환경변수보다 우선한다.
        contextRunner
            .withBean(BuildProperties.class, () -> new BuildProperties(buildInfo("1.2.3")))
            .withPropertyValues(
                "release.version=1.0.0", "release.backend-version=9.9.9", "release.git-sha=abc1234")
            .run(context -> assertThat(context.getBean(VersionInfo.class))
                .isEqualTo(new VersionInfo("1.0.0", "1.2.3", "abc1234")));
    }

    // build-info 가 없더라도 배포 파이프라인이 compose 로 넣어준 값이 있으면 그걸 쓴다.
    @Test
    void 빌드_정보가_없으면_주입된_환경변수_값을_쓴다() {
        contextRunner
            .withPropertyValues(
                "release.version=1.0.0", "release.backend-version=1.2.3", "release.git-sha=abc1234")
            .run(context -> assertThat(context.getBean(VersionInfo.class))
                .isEqualTo(new VersionInfo("1.0.0", "1.2.3", "abc1234")));
    }

    // bootJar 산출물이 아닌 로컬·IDE 실행에는 build-info 도 주입값도 없다. 그래도 기동해야 한다.
    @Test
    void 빌드_정보도_주입값도_없으면_기동에_실패하지_않고_unknown으로_떨어진다() {
        contextRunner
            .withPropertyValues("release.version=unknown", "release.backend-version=unknown",
                "release.git-sha=unknown")
            .run(context -> assertThat(context.getBean(VersionInfo.class))
                .isEqualTo(new VersionInfo(VersionInfo.UNKNOWN, VersionInfo.UNKNOWN, VersionInfo.UNKNOWN)));
    }

    private Properties buildInfo(String version) {
        Properties properties = new Properties();
        properties.setProperty("version", version);
        return properties;
    }
}
