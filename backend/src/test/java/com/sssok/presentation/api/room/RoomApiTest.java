package com.sssok.presentation.api.room;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

// Room 생성 -> 코드로 조회까지 컨트롤러/서비스/리포지토리/Flyway 마이그레이션이
// 실제 PostgreSQL 위에서 전부 맞물려 도는지 확인하는 관통 테스트 (Walking Skeleton).
// Flyway가 만든 스키마와 엔티티 매핑이 실제로 맞는지까지 검증한다 (운영과 동일한 검증 모드).
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@Testcontainers
class RoomApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 방을_생성하고_코드로_조회한다() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"우테코 회식\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.code").value(matchesPattern("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}")))
            .andExpect(jsonPath("$.data.name").value("우테코 회식"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String code = created.get("data").get("code").asText();

        mockMvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value(code))
            .andExpect(jsonPath("$.data.name").value("우테코 회식"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void 이름_없이_생성하면_400() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_NAME"));
    }

    @Test
    void 존재하지_않는_코드로_조회하면_404() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{code}", "23456789"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 형식이_잘못된_코드로_조회하면_400() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{code}", "invalid-code"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_CODE"));
    }
}
