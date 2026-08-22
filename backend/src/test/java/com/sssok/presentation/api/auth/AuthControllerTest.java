package com.sssok.presentation.api.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.domain.member.exception.InvalidNicknameException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// AuthController의 요청/응답 매핑과 예외 -> 에러 응답 변환만 검증하는 슬라이스 테스트.
// Service는 Mock으로 대체하므로 실제 회원 생성·토큰 발급 로직은 이 테스트의 관심사가 아니다.
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnonymousAuthService anonymousAuthService;

    @Test
    void 익명_인증에_성공하면_201과_토큰_정보를_반환한다() throws Exception {
        given(anonymousAuthService.authenticate(anyString()))
            .willReturn(new AuthResult("token-value", 1L, "민수", Instant.parse("2026-09-17T05:30:00Z")));

        mockMvc.perform(post("/auth/anonymous")
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

        mockMvc.perform(post("/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
    }
}
