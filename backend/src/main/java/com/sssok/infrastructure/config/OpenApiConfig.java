package com.sssok.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info()
                .title("sssOK API")
                .description("링크 하나로 여는 단발성 이미지·영상 공유 공간, sssOK의 백엔드 API 명세")
                .version("v0.0.1")
                .contact(new Contact()
                    .name("sssOK Backend")
                    .url("https://github.com/woowacourse-teams/2026-sssOK")));
    }
}
