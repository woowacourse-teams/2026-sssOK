package com.sssok.presentation.auth;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AuthMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthMember.class)
            && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        String authorizationHeader = request.getHeader("Authorization");
        String tokenParam = request.getParameter("token");

        // 헤더/쿼리 둘 다 안 보낸 경우만 통과시킨다. 보냈는데 잘못된 토큰이면 401.
        if (isMissing(authorizationHeader) && isMissing(tokenParam) && !isRequired(parameter)) {
            return null;
        }
        return tokenProvider.parse(extractToken(authorizationHeader, tokenParam));
    }

    private boolean isRequired(MethodParameter parameter) {
        AuthMember annotation = parameter.getParameterAnnotation(AuthMember.class);
        return annotation == null || annotation.required();
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    // EventSource(SSE)는 커스텀 헤더를 못 붙이므로, Authorization 헤더가 없으면 token 쿼리 파라미터로 대체 인증한다.
    private String extractToken(String authorizationHeader, String tokenParam) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        throw new UnauthorizedException("다시 접속해주세요");
    }
}