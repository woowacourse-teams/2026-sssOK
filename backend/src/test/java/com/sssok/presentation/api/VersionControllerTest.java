package com.sssok.presentation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.port.out.AdminTokenProvider;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.port.out.TokenProvider;
import com.sssok.common.version.VersionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 슬라이스 테스트
@WebMvcTest(VersionController.class)
@Import(VersionControllerTest.TestVersionInfo.class)
class VersionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TokenProvider tokenProvider;

    @MockitoBean
    AdminTokenProvider adminTokenProvider;

    @MockitoBean
    RoomRepository roomRepository;

    @MockitoBean
    RoomMemberRepository roomMemberRepository;

    @Test
    void 버전_정보를_ApiResponse로_감싸지_않고_그대로_반환한다() throws Exception {
        mockMvc.perform(get("/version"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.releaseVersion").value("1.0.0"))
            .andExpect(jsonPath("$.backendVersion").value("1.0.1"))
            .andExpect(jsonPath("$.gitSha").value("abc1234"));
    }

    @TestConfiguration
    static class TestVersionInfo {

        @Bean
        VersionInfo versionInfo() {
            return new VersionInfo("1.0.0", "1.0.1", "abc1234");
        }
    }
}
