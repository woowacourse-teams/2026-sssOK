package com.sssok.presentation.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
}
