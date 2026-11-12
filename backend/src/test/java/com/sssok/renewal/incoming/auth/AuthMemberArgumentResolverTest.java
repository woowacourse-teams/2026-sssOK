package com.sssok.renewal.incoming.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sssok.renewal.outgoing.security.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class AuthMemberArgumentResolverTest {

    private final TokenProvider tokenProvider = mock(TokenProvider.class);
    private final AuthMemberArgumentResolver resolver = new AuthMemberArgumentResolver(tokenProvider);
    private MockHttpServletRequest request;
    private MethodParameter parameter;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        request = new MockHttpServletRequest();
        parameter = new MethodParameter(
            getClass().getDeclaredMethod("handler", Long.class), 0);
    }

    @Test
    void 같은_요청에서_이미_검증한_토큰이면_다시_검증하지_않는다() {
        request.addHeader("Authorization", "Bearer token-a");
        VerifiedAuthentication.remember(request, "token-a", 7L);

        Object memberId = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);

        assertThat(memberId).isEqualTo(7L);
        verify(tokenProvider, never()).parse("token-a");
    }

    @Test
    void 앞서_검증한_토큰과_다른_토큰이_오면_새로_검증한다() {
        request.addHeader("Authorization", "Bearer token-b");
        VerifiedAuthentication.remember(request, "token-a", 7L);
        given(tokenProvider.parse("token-b")).willReturn(9L);

        Object memberId = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);

        assertThat(memberId).isEqualTo(9L);
        verify(tokenProvider).parse("token-b");
    }

    @Test
    void 검증한_기록이_없으면_토큰을_검증한다() {
        request.addHeader("Authorization", "Bearer token-a");
        given(tokenProvider.parse("token-a")).willReturn(7L);

        Object memberId = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);

        assertThat(memberId).isEqualTo(7L);
        verify(tokenProvider).parse("token-a");
    }

    @SuppressWarnings("unused")
    private void handler(@AuthMember Long memberId) {
    }
}
