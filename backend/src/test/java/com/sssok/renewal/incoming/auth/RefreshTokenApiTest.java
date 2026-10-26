package com.sssok.renewal.incoming.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.support.PostgresApiTest;
import com.sssok.support.PostgresContainerSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@PostgresApiTest
class RefreshTokenApiTest extends PostgresContainerSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void 웹은_본문에_refresh를_노출하지_않고_쿠키로_발급_갱신_삭제한다() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/anonymous")
                .header("X-CSRF-Protection", "1").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"웹\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("Secure")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();
        Cookie first = login.getResponse().getCookie(AuthTokenHttpSupport.COOKIE_NAME);
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first)
                .header("X-CSRF-Protection", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();
        Cookie next = refreshed.getResponse().getCookie(AuthTokenHttpSupport.COOKIE_NAME);
        assertThat(next.getValue()).isNotEqualTo(first.getValue());
        assertThat(data(refreshed).get("userId")).isEqualTo(data(login).get("userId"));
        mvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + data(refreshed).get("accessToken").asText()))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(next).header("X-CSRF-Protection", "1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 앱은_같은_API에서_본문으로_주고받으며_쿠키를_발급하지_않는다() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"앱\",\"refreshTokenDelivery\":\"BODY\"}"))
            .andExpect(status().isCreated()).andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(jsonPath("$.data.refreshToken").isString()).andReturn();
        String refresh = data(login).get("refreshToken").asText();
        MvcResult next = mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(data(next).get("refreshToken").asText()).isNotEqualTo(refresh);
    }

    @Test
    void 쿠키_갱신은_CSRF_헤더가_없거나_허용되지_않은_출처면_거절한다() throws Exception {
        Cookie cookie = new Cookie(AuthTokenHttpSupport.COOKIE_NAME, "r".repeat(43));
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)
                .header("X-CSRF-Protection", "1").header("Origin", "https://untrusted.example"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/anonymous").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"웹\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 두_전달방식_혼용과_토큰_누락을_거절한다() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthTokenHttpSupport.COOKIE_NAME, "r".repeat(43)))
                .header("X-CSRF-Protection", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + "b".repeat(43) + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/anonymous").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"앱\",\"refreshTokenDelivery\":\"UNKNOWN\"}"))
            .andExpect(status().isBadRequest());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
