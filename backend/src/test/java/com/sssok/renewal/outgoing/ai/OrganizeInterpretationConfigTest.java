package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.OrganizeRequestInterpreter;
import com.sssok.renewal.outgoing.config.OrganizeInterpretationConfig;
import com.sssok.renewal.outgoing.config.OrganizeInterpretationProperties;
import com.sssok.renewal.outgoing.config.OrganizeMcpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OrganizeInterpretationConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(OrganizeInterpretationConfig.class)
        .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void 기본값은_비활성화이며_키_없이_시작한다() {
        context.run(result -> assertThat(result).hasNotFailed().doesNotHaveBean(OrganizeRequestInterpreter.class));
    }

    @Test
    void 활성화하려면_키와_모델이_필요하다() {
        context.withPropertyValues("gallery.organization.interpretation.enabled=true")
            .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void 활성화하면_해석_인터페이스에_OpenAI_구현을_연결한다() {
        enabledContext().run(result -> {
            assertThat(result).hasNotFailed().hasSingleBean(OrganizeRequestInterpreter.class);
            assertThat(result.getBean(OrganizeRequestInterpreter.class))
                .isInstanceOf(OpenAiOrganizeRequestInterpreter.class);
            assertThat(result).hasSingleBean(McpOrganizeToolExecutor.class);
        });
    }

    @Test
    void 잘못된_시간_제한은_기동할_때_거절한다() {
        enabledContext().withPropertyValues("gallery.organization.interpretation.timeout=0s")
            .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void 추론_강도는_선택_설정이며_비워도_기동한다() {
        enabledContext().withPropertyValues("gallery.organization.interpretation.reasoning-effort=")
            .run(result -> assertThat(result).hasNotFailed().hasSingleBean(OrganizeRequestInterpreter.class));
        enabledContext().withPropertyValues("gallery.organization.interpretation.reasoning-effort=none")
            .run(result -> assertThat(result).hasNotFailed().hasSingleBean(OrganizeRequestInterpreter.class));
    }

    @Test
    void 기능이_꺼진_기본_설정에서도_설정_객체가_바인딩된다() {
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BindOnly.class)
            .run(result -> assertThat(result).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({OrganizeInterpretationProperties.class, OrganizeMcpProperties.class})
    static class BindOnly {
    }

    private ApplicationContextRunner enabledContext() {
        return context.withPropertyValues(
            "gallery.organization.interpretation.enabled=true",
            "gallery.organization.interpretation.api-key=test-key",
            "gallery.organization.interpretation.model=test-model",
            "gallery.organization.mcp.url=http://localhost:8818/mcp",
            "gallery.organization.mcp.token=test-token");
    }
}
