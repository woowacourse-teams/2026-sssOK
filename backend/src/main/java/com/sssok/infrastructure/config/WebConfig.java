package com.sssok.infrastructure.config;

import com.sssok.presentation.api.common.RoomMembershipInterceptor;
import com.sssok.presentation.auth.AuthMemberArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    // 실제 API 베이스 URL: https://api.ssssok.com/api/v1
    // 헬스체크(HealthController, /health)는 배포 healthcheck가 그대로 참조하므로 접두사에서 제외한다
    // — presentation.api "하위 패키지"에 있는 컨트롤러에만 붙도록 해서 자동으로 걸러진다.
    private static final String API_PREFIX = "/api/v1";
    private static final String API_SUB_PACKAGE_PREFIX = "com.sssok.presentation.api.";

    private final AuthMemberArgumentResolver authMemberArgumentResolver;
    private final RoomMembershipInterceptor roomMembershipInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authMemberArgumentResolver);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
            controllerType -> controllerType.getPackageName().startsWith(API_SUB_PACKAGE_PREFIX));
    }

    // 방 존재/만료/입장 여부(404·410·403)를 폴더·미디어 API 공통으로 검증한다.
    // 방 자체를 다루는 RoomController는 호스트 권한으로 별도 판정하므로 대상에서 뺀다.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roomMembershipInterceptor)
            .addPathPatterns(
                API_PREFIX + "/rooms/*/folders/**",
                API_PREFIX + "/rooms/*/media",
                API_PREFIX + "/rooms/*/media/**",
                API_PREFIX + "/rooms/*/downloads",
                API_PREFIX + "/rooms/*/downloads/**");
    }

    // 프론트가 API와 다른 오리진에서 서빙되므로 브라우저가 프리플라이트(OPTIONS)를 보낸다.
    // 인증은 쿠키가 아니라 Authorization 헤더라 credentials는 필요 없다.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(API_PREFIX + "/**")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
            .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
