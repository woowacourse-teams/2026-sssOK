package com.sssok.presentation.api.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.auth.IssueLinkCodeService;
import com.sssok.application.auth.LinkCodeResult;
import com.sssok.application.auth.LinkLoginService;
import com.sssok.application.auth.exception.LinkCodeExpiredException;
import com.sssok.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.domain.auth.exception.InvalidLinkCodeException;
import com.sssok.domain.member.exception.InvalidNicknameException;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// AuthController의 요청/응답 매핑과 예외 -> 에러 응답 변환만 검증하는 슬라이스 테스트.
// Service는 Mock으로 대체하므로 실제 회원 생성·토큰 발급·코드 발급 로직은 이 테스트의 관심사가 아니다.
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnonymousAuthService anonymousAuthService;

    @MockitoBean
    IssueLinkCodeService issueLinkCodeService;

    @MockitoBean
    LinkLoginService linkLoginService;

    // AuthMemberArgumentResolver가 의존하는 포트 — Authorization 헤더 파싱 검증에 필요하다.
    @MockitoBean
    TokenProvider tokenProvider;

    @Test
    void 익명_인증에_성공하면_201과_토큰_정보를_반환한다() throws Exception {
        given(anonymousAuthService.authenticate(anyString()))
            .willReturn(new AuthResult("token-value", 1L, "민수", Instant.parse("2026-09-17T05:30:00Z")));

        mockMvc.perform(post("/api/v1/auth/anonymous")
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
        given(anonymousAuthService.authenticate(anyString()))
            .willThrow(new InvalidNicknameException("닉네임을 입력해주세요"));

        mockMvc.perform(post("/api/v1/auth/anonymous")
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
        given(linkLoginService.login("483920"))
            .willReturn(new AuthResult("token-value", 1L, "민수", Instant.parse("2026-09-17T05:30:00Z")));

        mockMvc.perform(post("/api/v1/auth/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"483920\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("token-value"))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.nickname").value("민수"));
    }

    @Test
    void 형식이_잘못된_코드면_400을_반환한다() throws Exception {
        given(linkLoginService.login(anyString())).willThrow(new InvalidLinkCodeException("abc"));

        mockMvc.perform(post("/api/v1/auth/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"abc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LINK_CODE"));
    }

    @Test
    void 존재하지_않는_코드면_404를_반환한다() throws Exception {
        given(linkLoginService.login(anyString())).willThrow(new LinkCodeNotFoundException());

        mockMvc.perform(post("/api/v1/auth/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"999999\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("LINK_CODE_NOT_FOUND"));
    }

    @Test
    void 만료된_코드면_410을_반환한다() throws Exception {
        given(linkLoginService.login(anyString())).willThrow(new LinkCodeExpiredException());

        mockMvc.perform(post("/api/v1/auth/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"999999\"}"))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value("LINK_CODE_EXPIRED"));
    }

    @Test
    void 매핑되지_않은_예외는_500과_일관된_에러_형식으로_반환된다() throws Exception {
        given(linkLoginService.login(anyString())).willThrow(new NoSuchElementException());

        mockMvc.perform(post("/api/v1/auth/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkCode\":\"999999\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
