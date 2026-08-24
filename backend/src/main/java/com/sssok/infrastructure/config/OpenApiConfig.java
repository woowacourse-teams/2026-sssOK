package com.sssok.infrastructure.config;

import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info()
                .title("sssOK API")
                .description("링크 하나로 여는 단발성 이미지·영상 공유 공간, sssOK의 백엔드 API 명세")
                .version("v0.0.1")
                .contact(new Contact()
                    .name("sssOK Backend")
                    .url("https://github.com/woowacourse-teams/2026-sssOK")))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                    .name(BEARER_AUTH)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }

    // 컨트롤러 메서드 파라미터에 @AuthMember가 붙어 있으면 자동으로 잠금 표시를 붙임
    @Bean
    public OperationCustomizer authMemberSecurityCustomizer() {
        return (operation, handlerMethod) -> {
            boolean requiresAuth = Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(AuthMember.class));
            if (requiresAuth) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
            }
            return operation;
        };
    }
}
