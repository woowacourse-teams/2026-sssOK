package com.sssok.presentation.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.infrastructure.persistence.admin.AdminJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// API 인수 테스트 — 마이그레이션으로 심은 초기 계정으로 로그인해 관리자 API 를 관통 확인한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
class AdminApiTest extends PostgresContainerSupport {

    // V19 마이그레이션이 심는 초기 계정. 비밀번호는 테스트에서 바꿔 쓸 수 없으므로,
    // 로그인이 필요한 케이스는 슈퍼관리자가 만든 새 계정으로 확인한다.
    private static final String SUPER_ADMIN_LOGIN_ID = "superadmin";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AdminJpaRepository adminJpaRepository;

    @Test
    void 마이그레이션이_초기_계정_4개를_심는다() {
        assertThat(adminJpaRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(adminJpaRepository.findByLoginId(SUPER_ADMIN_LOGIN_ID)).isPresent();
        assertThat(adminJpaRepository.countByRole("SUPER_ADMIN")).isEqualTo(1);
        assertThat(adminJpaRepository.countByRole("ADMIN")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void 초기_계정의_비밀번호는_평문이_아니라_BCrypt_해시로_저장된다() {
        String hash = adminJpaRepository.findByLoginId(SUPER_ADMIN_LOGIN_ID).orElseThrow()
            .getPasswordHash();

        assertThat(hash).startsWith("$2a$").hasSize(60);
    }

    @Test
    void 없는_아이디로_로그인하면_401이고_비밀번호_오류와_구분되지_않는다() throws Exception {
        로그인("nosuchadmin", "whatever12")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 비밀번호가_틀리면_401이고_아이디_오류와_같은_응답이다() throws Exception {
        로그인(SUPER_ADMIN_LOGIN_ID, "wrongpassword")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 아이디나_비밀번호를_보내지_않으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"superadmin\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void 익명_사용자_토큰으로_관리자_API를_호출하면_401이다() throws Exception {
        String memberToken = 익명_인증("가현");

        mockMvc.perform(get("/api/v1/admin/accounts")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_없이_관리자_API를_호출하면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_토큰으로_익명_사용자용_API를_호출하면_401이다() throws Exception {
        String adminToken = 슈퍼관리자_토큰();

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"관리자가 만든 방\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 슈퍼관리자는_계정_목록을_조회한다() throws Exception {
        String token = 슈퍼관리자_토큰();

        mockMvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accounts").isArray())
            .andExpect(jsonPath("$.data.accounts[0].loginId").value(Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.accounts[0].role").value(Matchers.notNullValue()));
    }

    @Test
    void 계정_목록_응답에는_비밀번호가_들어가지_않는다() throws Exception {
        String token = 슈퍼관리자_토큰();

        String body = mockMvc.perform(
                get("/api/v1/admin/accounts").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    void 슈퍼관리자는_계정을_만든다() throws Exception {
        String token = 슈퍼관리자_토큰();

        계정_생성(token, "newadmin1", "password123", "새관리자", "ADMIN")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.loginId").value("newadmin1"))
            .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void 같은_아이디로_또_만들면_409() throws Exception {
        String token = 슈퍼관리자_토큰();
        계정_생성(token, "dupadmin", "password123", "중복", "ADMIN").andExpect(status().isCreated());

        계정_생성(token, "dupadmin", "password123", "중복2", "ADMIN")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ADMIN_LOGIN_ID"));
    }

    @Test
    void 짧은_비밀번호로_만들면_400() throws Exception {
        String token = 슈퍼관리자_토큰();

        계정_생성(token, "shortpw", "1234", "짧은비번", "ADMIN")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ADMIN_PASSWORD"));
    }

    @Test
    void 형식에_맞지_않는_아이디로_만들면_400() throws Exception {
        String token = 슈퍼관리자_토큰();

        계정_생성(token, "AB", "password123", "짧은아이디", "ADMIN")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ADMIN_LOGIN_ID"));
    }

    @Test
    void 일반_관리자가_계정을_만들면_403() throws Exception {
        String superToken = 슈퍼관리자_토큰();
        계정_생성(superToken, "plainadmin", "password123", "일반관리자", "ADMIN")
            .andExpect(status().isCreated());
        String plainToken = 로그인_토큰("plainadmin", "password123");

        계정_생성(plainToken, "anotheradmin", "password123", "또다른", "ADMIN")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPER_ADMIN_REQUIRED"));
    }

    @Test
    void 일반_관리자도_계정_목록은_조회할_수_있다() throws Exception {
        String superToken = 슈퍼관리자_토큰();
        계정_생성(superToken, "readeradmin", "password123", "조회자", "ADMIN")
            .andExpect(status().isCreated());
        String plainToken = 로그인_토큰("readeradmin", "password123");

        mockMvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + plainToken))
            .andExpect(status().isOk());
    }

    @Test
    void 슈퍼관리자는_계정의_이름을_바꾼다() throws Exception {
        String token = 슈퍼관리자_토큰();
        long targetId = 계정_만들기(token, "renameme", "password123", "이전이름");

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}", targetId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새이름\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("새이름"));
    }

    @Test
    void 아무_항목도_보내지_않고_수정하면_400() throws Exception {
        String token = 슈퍼관리자_토큰();
        long targetId = 계정_만들기(token, "emptypatch", "password123", "빈수정");

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}", targetId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_PATCH"));
    }

    @Test
    void 없는_계정을_수정하면_404() throws Exception {
        String token = 슈퍼관리자_토큰();

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}", -1L)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"없는계정\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADMIN_NOT_FOUND"));
    }

    @Test
    void 마지막_슈퍼관리자를_강등하면_409() throws Exception {
        String token = 슈퍼관리자_토큰();
        long superId = adminJpaRepository.findByLoginId(SUPER_ADMIN_LOGIN_ID).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}", superId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LAST_SUPER_ADMIN"));
    }

    @Test
    void 마지막_슈퍼관리자를_삭제하면_409() throws Exception {
        String token = 슈퍼관리자_토큰();
        long superId = adminJpaRepository.findByLoginId(SUPER_ADMIN_LOGIN_ID).orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/admin/accounts/{id}", superId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LAST_SUPER_ADMIN"));
    }

    @Test
    void 슈퍼관리자는_일반_관리자를_삭제한다() throws Exception {
        String token = 슈퍼관리자_토큰();
        long targetId = 계정_만들기(token, "deleteme", "password123", "삭제대상");

        mockMvc.perform(delete("/api/v1/admin/accounts/{id}", targetId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedAdminId").value(targetId));

        assertThat(adminJpaRepository.findById(targetId)).isEmpty();
    }

    @Test
    void 삭제된_계정으로는_로그인할_수_없다() throws Exception {
        String token = 슈퍼관리자_토큰();
        long targetId = 계정_만들기(token, "goneadmin", "password123", "사라질계정");
        mockMvc.perform(delete("/api/v1/admin/accounts/{id}", targetId)
            .header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        로그인("goneadmin", "password123")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 삭제된_계정의_토큰으로는_관리자_API를_쓸_수_없다() throws Exception {
        String superToken = 슈퍼관리자_토큰();
        long targetId = 계정_만들기(superToken, "revoked", "password123", "삭제될계정");
        String revokedToken = 로그인_토큰("revoked", "password123");
        mockMvc.perform(delete("/api/v1/admin/accounts/{id}", targetId)
            .header("Authorization", "Bearer " + superToken)).andExpect(status().isOk());

        // 토큰 자체는 아직 만료되지 않았지만, 요청마다 계정을 다시 읽어 권한을 판정하므로 막힌다.
        mockMvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + revokedToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
    }

    private ResultActions 로그인(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    private String 로그인_토큰(String loginId, String password) throws Exception {
        MvcResult result = 로그인(loginId, password).andExpect(status().isOk()).andReturn();
        return 값(result, "accessToken");
    }

    // 초기 슈퍼관리자의 비밀번호는 테스트가 모르므로, 슈퍼관리자 권한이 필요할 때는
    // 그 계정의 비밀번호를 테스트용으로 바꿔 두고 로그인한다.
    private String 슈퍼관리자_토큰() throws Exception {
        var entity = adminJpaRepository.findByLoginId(SUPER_ADMIN_LOGIN_ID).orElseThrow();
        adminJpaRepository.save(new com.sssok.infrastructure.persistence.admin.AdminJpaEntity(
            entity.getId(),
            entity.getLoginId(),
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("testpass123"),
            entity.getName(),
            entity.getRole(),
            entity.getCreatedAt()
        ));
        return 로그인_토큰(SUPER_ADMIN_LOGIN_ID, "testpass123");
    }

    private ResultActions 계정_생성(
        String token, String loginId, String password, String name, String role
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/accounts")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password
                + "\",\"name\":\"" + name + "\",\"role\":\"" + role + "\"}"));
    }

    private long 계정_만들기(String token, String loginId, String password, String name)
        throws Exception {
        MvcResult created = 계정_생성(token, loginId, password, name, "ADMIN")
            .andExpect(status().isCreated()).andReturn();
        return Long.parseLong(값(created, "adminId"));
    }

    private String 값(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get(field).asText();
    }

    private String 익명_인증(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }
}
