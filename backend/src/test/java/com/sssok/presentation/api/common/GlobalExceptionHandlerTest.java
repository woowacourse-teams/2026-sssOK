package com.sssok.presentation.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.infrastructure.config.WebConfig;
import com.sssok.presentation.api.HealthController;
import com.sssok.presentation.auth.AuthAdminArgumentResolver;
import com.sssok.presentation.auth.AuthMemberArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(WebConfig.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthMemberArgumentResolver authMemberArgumentResolver;

    @MockitoBean
    AuthAdminArgumentResolver authAdminArgumentResolver;

    @MockitoBean
    RoomMembershipInterceptor roomMembershipInterceptor;

    @Test
    void 존재하지_않는_API_경로는_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("API_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("존재하지 않는 API 경로입니다"));
    }
}
