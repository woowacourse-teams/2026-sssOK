package com.sssok.infrastructure.config;

import com.sssok.presentation.auth.AuthMemberArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    // 실제 API 베이스 URL: https://api.ssssok.com/api/v1
    // 헬스체크(HealthController, /health)는 배포 healthcheck가 그대로 참조하므로 접두사에서 제외한다
    // — presentation.api "하위 패키지"에 있는 컨트롤러에만 붙도록 해서 자동으로 걸러진다.
    private static final String API_PREFIX = "/api/v1";
    private static final String API_SUB_PACKAGE_PREFIX = "com.sssok.presentation.api.";

    private final AuthMemberArgumentResolver authMemberArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authMemberArgumentResolver);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
            controllerType -> controllerType.getPackageName().startsWith(API_SUB_PACKAGE_PREFIX));
    }
}
