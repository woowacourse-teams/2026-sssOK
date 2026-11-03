package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.OrganizeRequestInterpreter;
import com.sssok.renewal.outgoing.config.OrganizeInterpretationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

    private ApplicationContextRunner enabledContext() {
        return context.withPropertyValues(
            "gallery.organization.interpretation.enabled=true",
            "gallery.organization.interpretation.api-key=test-key",
            "gallery.organization.interpretation.model=test-model",
            "gallery.organization.mcp.url=http://localhost:8818/mcp",
            "gallery.organization.mcp.token=test-token");
    }
}
