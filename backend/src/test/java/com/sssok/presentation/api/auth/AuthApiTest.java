package com.sssok.presentation.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// API 인수 테스트 (docs/backend/TEST_CONVENTION.md 참고)
// 닉네임으로 익명 회원을 생성하고 토큰을 발급받는 흐름이 실제 PostgreSQL 위에서 맞물려 도는지 확인한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
class AuthApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 닉네임으로_익명_인증하면_토큰과_userId를_발급받는다() throws Exception {
        mockMvc.perform(post("/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"민수\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.data.userId").value(notNullValue()))
            .andExpect(jsonPath("$.data.nickname").value("민수"))
            .andExpect(jsonPath("$.data.expiresAt").value(notNullValue()));
    }

    @Test
    void 호출할_때마다_다른_회원이_생성된다() throws Exception {
        MvcResult first = mockMvc.perform(post("/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"민수\"}"))
            .andReturn();
        MvcResult second = mockMvc.perform(post("/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"민수\"}"))
            .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(firstBody.get("data").get("userId").asLong())
            .isNotEqualTo(secondBody.get("data").get("userId").asLong());
    }

    @Test
    void 닉네임_없이_요청하면_400() throws Exception {
        mockMvc.perform(post("/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
    }

    @Test
    void 발급받은_토큰으로_연결_코드를_발급받는다() throws Exception {
        String accessToken = 익명_인증으로_토큰_발급받기();

        mockMvc.perform(post("/auth/link-code")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.linkCode").value(matchesPattern("\\d{6}")))
            .andExpect(jsonPath("$.data.expiresAt").value(notNullValue()));
    }

    @Test
    void 같은_토큰으로_다시_발급받으면_이전_코드가_무효화된다() throws Exception {
        String accessToken = 익명_인증으로_토큰_발급받기();

        MvcResult first = mockMvc.perform(post("/auth/link-code")
                .header("Authorization", "Bearer " + accessToken))
            .andReturn();
        MvcResult second = mockMvc.perform(post("/auth/link-code")
                .header("Authorization", "Bearer " + accessToken))
            .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(firstBody.get("data").get("linkCode").asText())
            .isNotEqualTo(secondBody.get("data").get("linkCode").asText());
    }

    @Test
    void Authorization_헤더_없이_연결_코드를_요청하면_401() throws Exception {
        mockMvc.perform(post("/auth/link-code"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 형식이_잘못된_토큰으로_요청하면_401() throws Exception {
        mockMvc.perform(post("/auth/link-code")
                .header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private String 익명_인증으로_토큰_발급받기() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"민수\"}"))
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }
}
