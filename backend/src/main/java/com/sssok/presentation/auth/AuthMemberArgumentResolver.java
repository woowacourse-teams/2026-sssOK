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
        String token = extractToken(request.getHeader("Authorization"));
        return tokenProvider.parse(token);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
