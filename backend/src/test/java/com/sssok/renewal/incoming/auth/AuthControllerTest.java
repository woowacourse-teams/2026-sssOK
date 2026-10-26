package com.sssok.renewal.incoming.auth;

import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.application.auth.CompleteSocialLoginService;
import com.sssok.renewal.application.auth.IssueLinkCodeService;
import com.sssok.renewal.application.auth.LinkCodeResult;
import com.sssok.renewal.application.auth.LinkLoginService;
import com.sssok.renewal.application.auth.SessionTokenProvider;
import com.sssok.renewal.application.auth.SocialLoginStartResult;
import com.sssok.renewal.application.auth.StartAppleLoginService;
import com.sssok.renewal.application.auth.StartKakaoLoginService;
import com.sssok.renewal.application.auth.WithdrawAccountService;
import com.sssok.renewal.application.auth.exception.LinkCodeExpiredException;
import com.sssok.renewal.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.auth.exception.InvalidLinkCodeException;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.domain.member.exception.InvalidNicknameException;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// AuthController의 요청/응답 매핑과 예외 -> 에러 응답 변환만 검증하는 슬라이스 테스트.
// Service는 Mock으로 대체하므로 실제 회원 생성·토큰 발급·코드 발급 로직은 이 테스트의 관심사가 아니다.
@Import(AuthTokenHttpSupport.class)
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WithdrawAccountService withdrawAccountService;

    @MockitoBean
    CompleteSocialLoginService completeSocialLoginService;

    @MockitoBean
    StartAppleLoginService startAppleLoginService;

    @MockitoBean
    StartKakaoLoginService startKakaoLoginService;

    @MockitoBean
    AnonymousAuthService anonymousAuthService;

    @MockitoBean
    IssueLinkCodeService issueLinkCodeService;

    @MockitoBean
    LinkLoginService linkLoginService;

    // AuthMemberArgumentResolver가 의존하는 포트 — Authorization 헤더 파싱 검증에 필요하다.
    @MockitoBean
    SessionTokenProvider tokenProvider;

    @MockitoBean
    AdminTokenProvider adminTokenProvider;

    // WebConfig가 등록하는 RoomMembershipInterceptor가 요구하는 포트. 이 슬라이스의 경로는
    // 인터셉터 패턴과 무관하지만, 빈 자체는 컨텍스트에 함께 뜨므로 목으로 채워야 한다.
    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @Test
    void 탈퇴는_인증한_회원만_전달하고_204를_반환한다() throws Exception {
        given(tokenProvider.parse("service-token")).willReturn(12L);
        mockMvc.perform(delete("/api/v1/auth/account")
                .header("Authorization", "Bearer service-token"))
            .andExpect(status().isNoContent());
        verify(withdrawAccountService).withdraw(12L);
    }

    @Test
    void 탈퇴는_인증이_없으면_거부한다() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/account"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(withdrawAccountService);
    }

    @Test
    void 애플_시작은_익명_인증없이_URL과_확인값을_반환한다() throws Exception {
        given(startAppleLoginService.start(null)).willReturn(appleStartResult());
        mockMvc.perform(post("/api/v1/auth/oauth/apple/start"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(jsonPath("$.data.authorizationUrl").value(appleStartResult().authorizationUrl()))
            .andExpect(jsonPath("$.data.state").value("server-state"))
            .andExpect(jsonPath("$.data.loginVerifier").value("private-verifier"))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void 애플_시작은_익명_토큰을_전달한다() throws Exception {
        given(startAppleLoginService.start("anonymous-token")).willReturn(appleStartResult());
        mockMvc.perform(post("/api/v1/auth/oauth/apple/start").header("Authorization", "Bearer anonymous-token"))
            .andExpect(status().isOk());
        verify(startAppleLoginService).start("anonymous-token");
    }

    @Test
    void 비활성_애플_시작은_503이다() throws Exception {
        given(startAppleLoginService.start(null)).willThrow(SocialAuthException.unavailable());
        mockMvc.perform(post("/api/v1/auth/oauth/apple/start")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void 애플_시작의_잘못된_인증헤더는_익명으로_처리하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/apple/start").header("Authorization", "Basic token"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(startAppleLoginService);
    }

    @Test
    void 애플_완료는_애플_제공자를_선택하고_서비스_토큰만_반환한다() throws Exception {
        given(completeSocialLoginService.login(SocialProvider.APPLE, "code", "s".repeat(43), "v".repeat(43), RefreshTokenDelivery.COOKIE))
            .willReturn(new AuthResult("service-token", 20L, "민수", Instant.parse("2026-11-01T00:00:00Z"), "service-refresh", Instant.now().plusSeconds(2592000)));
        mockMvc.perform(post("/api/v1/auth/oauth/apple/login").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON).content(appleLoginBody()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Set-Cookie", Matchers.containsString("sssok_refresh=service-refresh")))
            .andExpect(jsonPath("$.data.accessToken").value("service-token"))
            .andExpect(jsonPath("$.data.userId").value(20))
            .andExpect(jsonPath("$.data.idToken").doesNotExist())
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        verify(completeSocialLoginService).login(SocialProvider.APPLE, "code", "s".repeat(43), "v".repeat(43), RefreshTokenDelivery.COOKIE);
    }

    @Test
    void 애플_완료의_누락된_입력은_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/apple/login").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(completeSocialLoginService);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 409, 503})
    void 애플_완료_실패의_상태코드를_유지한다(int statusCode) throws Exception {
        SocialAuthException error = switch (statusCode) {
            case 401 -> SocialAuthException.invalid();
            case 409 -> SocialAuthException.conflict();
            default -> SocialAuthException.unavailable();
        };
        given(completeSocialLoginService.login(SocialProvider.APPLE, "code", "s".repeat(43), "v".repeat(43), RefreshTokenDelivery.COOKIE)).willThrow(error);
        mockMvc.perform(post("/api/v1/auth/oauth/apple/login").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON).content(appleLoginBody()))
            .andExpect(status().is(statusCode));
    }

    private String appleLoginBody() {
        return "{\"code\":\"code\",\"state\":\"%s\",\"loginVerifier\":\"%s\"}"
            .formatted("s".repeat(43), "v".repeat(43));
    }

    private SocialLoginStartResult appleStartResult() {
        return new SocialLoginStartResult("https://appleid.apple.com/auth/authorize?state=server-state&nonce=server-nonce",
            "server-state", "private-verifier", Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void 카카오_완료는_서비스_토큰을_캐시없이_반환한다() throws Exception {
        given(completeSocialLoginService.login(SocialProvider.KAKAO, "code", "s".repeat(43), "v".repeat(43), RefreshTokenDelivery.COOKIE))
            .willReturn(new AuthResult("service-token", 10L, "민수", Instant.parse("2026-11-01T00:00:00Z"), "service-refresh", Instant.now().plusSeconds(2592000)));
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/login").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code\",\"state\":\"%s\",\"loginVerifier\":\"%s\"}"
                    .formatted("s".repeat(43), "v".repeat(43))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Set-Cookie", Matchers.containsString("sssok_refresh=service-refresh")))
            .andExpect(jsonPath("$.data.accessToken").value("service-token"))
            .andExpect(jsonPath("$.data.userId").value(10))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"code\":\"code\"}",
        "{\"code\":\"code\",\"state\":\"bad\",\"loginVerifier\":\"bad\"}"})
    void 잘못된_완료_요청은_인증_전에_거부한다(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/login").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(completeSocialLoginService);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 409, 503})
    void 카카오_완료_실패의_상태코드를_유지한다(int statusCode) throws Exception {
        SocialAuthException error = switch (statusCode) {
            case 401 -> SocialAuthException.invalid();
            case 409 -> SocialAuthException.conflict();
            default -> SocialAuthException.unavailable();
        };
        given(completeSocialLoginService.login(SocialProvider.KAKAO, "code", "s".repeat(43), "v".repeat(43), RefreshTokenDelivery.COOKIE)).willThrow(error);
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/login").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code\",\"state\":\"%s\",\"loginVerifier\":\"%s\"}"
                    .formatted("s".repeat(43), "v".repeat(43))))
            .andExpect(status().is(statusCode));
    }

    @Test
    void 인증_없이_카카오_로그인을_시작하고_쿠키_없이_확인값을_반환한다() throws Exception {
        given(startKakaoLoginService.start(null)).willReturn(kakaoStartResult());
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/start"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(jsonPath("$.data.authorizationUrl").value("https://kauth.kakao.com/oauth/authorize?state=test-state"))
            .andExpect(jsonPath("$.data.state").value("test-state"))
            .andExpect(jsonPath("$.data.loginVerifier").value("private-verifier"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-10-25T12:05:00Z"))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.nonce").doesNotExist());
    }

    @Test
    void 익명_토큰은_헤더에서만_읽어_로그인_시작에_전달한다() throws Exception {
        given(startKakaoLoginService.start("anonymous-token")).willReturn(kakaoStartResult());
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/start")
                .header("Authorization", "Bearer anonymous-token"))
            .andExpect(status().isOk());
        verify(startKakaoLoginService).start("anonymous-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Basic token", "Bearer ", "Bearer two tokens"})
    void 잘못된_인증_헤더를_인증_없는_요청으로_취급하지_않는다(String authorization) throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/start").header("Authorization", authorization))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(startKakaoLoginService);
    }

    @Test
    void 카카오_시작에서_만료된_익명_토큰은_401로_반환한다() throws Exception {
        given(startKakaoLoginService.start("expired")).willThrow(new UnauthorizedException("다시 접속해주세요"));
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/start").header("Authorization", "Bearer expired"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 카카오가_비활성이면_503을_반환한다() throws Exception {
        given(startKakaoLoginService.start(null)).willThrow(SocialAuthException.unavailable());
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/start"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SOCIAL_AUTH_UNAVAILABLE"));
    }

    private SocialLoginStartResult kakaoStartResult() {
        return new SocialLoginStartResult("https://kauth.kakao.com/oauth/authorize?state=test-state",
            "test-state", "private-verifier", Instant.parse("2026-10-25T12:05:00Z"));
    }

    @Test
    void 익명_인증에_성공하면_201과_토큰_정보를_반환한다() throws Exception {
        given(anonymousAuthService.authenticate(anyString(), eq(RefreshTokenDelivery.COOKIE)))
            .willReturn(new AuthResult("token-value", 1L, "민수", Instant.parse("2026-09-17T05:30:00Z"), "service-refresh", Instant.now().plusSeconds(2592000)));

        mockMvc.perform(post("/api/v1/auth/anonymous").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"민수\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.accessToken").value("token-value"))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("민수"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-09-17T05:30:00Z"));
    }

    @Test
    void 닉네임이_유효하지_않으면_400과_에러코드를_반환한다() throws Exception {
        given(anonymousAuthService.authenticate(anyString(), eq(RefreshTokenDelivery.COOKIE)))
            .willThrow(new InvalidNicknameException());

        mockMvc.perform(post("/api/v1/auth/anonymous").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
    }

    @Test
    void 유효한_토큰으로_연결_코드를_발급받으면_201을_반환한다() throws Exception {
        given(tokenProvider.parse("valid-token")).willReturn(1L);
        given(issueLinkCodeService.issue(1L))
            .willReturn(new LinkCodeResult("483920", Instant.parse("2026-08-22T04:15:00Z")));

        mockMvc.perform(post("/api/v1/auth/link-code")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.linkCode").value("483920"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-08-22T04:15:00Z"));
    }

    @Test
    void Authorization_헤더가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/link-code"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void token_쿼리_파라미터로는_인증되지_않는다() throws Exception {
        given(tokenProvider.parse("valid-token")).willReturn(1L);

        mockMvc.perform(post("/api/v1/auth/link-code").param("token", "valid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 토큰_검증에_실패하면_401을_반환한다() throws Exception {
        given(tokenProvider.parse(anyString())).willThrow(new UnauthorizedException("다시 접속해주세요"));

        mockMvc.perform(post("/api/v1/auth/link-code")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 유효한_연결_코드로_로그인하면_200과_토큰_정보를_반환한다() throws Exception {
        given(linkLoginService.login("483920", RefreshTokenDelivery.COOKIE))
            .willReturn(new AuthResult("token-value", 1L, "민수", Instant.parse("2026-09-17T05:30:00Z"), "service-refresh", Instant.now().plusSeconds(2592000)));

        mockMvc.perform(post("/api/v1/auth/link").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"483920\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("token-value"))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("민수"));
    }

    @Test
    void 형식이_잘못된_코드면_400을_반환한다() throws Exception {
        given(linkLoginService.login(anyString(), eq(RefreshTokenDelivery.COOKIE))).willThrow(new InvalidLinkCodeException("abc"));

        mockMvc.perform(post("/api/v1/auth/link").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"abc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LINK_CODE"));
    }

    @Test
    void 존재하지_않는_코드면_404를_반환한다() throws Exception {
        given(linkLoginService.login(anyString(), eq(RefreshTokenDelivery.COOKIE))).willThrow(new LinkCodeNotFoundException());

        mockMvc.perform(post("/api/v1/auth/link").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"999999\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("LINK_CODE_NOT_FOUND"));
    }

    @Test
    void 만료된_코드면_410을_반환한다() throws Exception {
        given(linkLoginService.login(anyString(), eq(RefreshTokenDelivery.COOKIE))).willThrow(new LinkCodeExpiredException());

        mockMvc.perform(post("/api/v1/auth/link").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"999999\"}"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("LINK_CODE_EXPIRED"));
    }

    @Test
    void 매핑되지_않은_예외는_500과_일관된_에러_형식으로_반환된다() throws Exception {
        given(linkLoginService.login(anyString(), eq(RefreshTokenDelivery.COOKIE))).willThrow(new NoSuchElementException());

        mockMvc.perform(post("/api/v1/auth/link").header("X-CSRF-Protection", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"999999\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
