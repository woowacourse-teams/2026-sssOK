package com.sssok.presentation.api.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

// API 인수 테스트 — 관리자가 의견의 처리 상태를 바꾸고 조회로 다시 확인한다.
class AdminFeedbackStatusApiTest extends AdminFeedbackApiSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 새로_남긴_의견은_미반영_상태다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "방금 남긴 의견");
        String adminToken = 슈퍼관리자_토큰();

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNREFLECTED"));
        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].status").value("UNREFLECTED"));
    }

    // 마이그레이션 전에 저장된 의견처럼 status 를 넣지 않고 저장한 행은 기본값으로 미반영이 된다.
    @Test
    void 상태_없이_저장된_기존_의견은_미반영으로_조회된다() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("""
            INSERT INTO feedback (content, room_id, room_name, member_id, created_at, updated_at)
            VALUES ('예전 의견', 1, '우테코 회식', 1, ?, ?)
            """, Timestamp.from(now), Timestamp.from(now));

        목록_조회(슈퍼관리자_토큰(), null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].content").value("예전 의견"))
            .andExpect(jsonPath("$.data.feedbacks[0].status").value("UNREFLECTED"));
    }

    @Test
    void 반영으로_바꾸면_다시_조회해도_반영으로_보인다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "썸네일이 늦게 떠요");
        String adminToken = 슈퍼관리자_토큰();

        상태_변경(adminToken, feedbackId, "REFLECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.content").value("썸네일이 늦게 떠요"))
            .andExpect(jsonPath("$.data.status").value("REFLECTED"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFLECTED"));
        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].status").value("REFLECTED"));
    }

    @Test
    void 반영된_의견을_미반영으로_되돌린다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "되돌릴 의견");
        String adminToken = 슈퍼관리자_토큰();
        상태_변경(adminToken, feedbackId, "REFLECTED").andExpect(status().isOk());

        상태_변경(adminToken, feedbackId, "UNREFLECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNREFLECTED"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNREFLECTED"));
    }

    @Test
    void 처리중과_보류로_바꾸면_다시_조회해도_그대로_보인다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "다운로드가 끊겨요");
        String adminToken = 슈퍼관리자_토큰();

        상태_변경(adminToken, feedbackId, "IN_PROGRESS")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        상태_변경(adminToken, feedbackId, "ON_HOLD")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ON_HOLD"));
        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].status").value("ON_HOLD"));
    }

    @Test
    void 같은_상태로_다시_바꿔도_성공한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "두 번 반영");
        String adminToken = 슈퍼관리자_토큰();
        상태_변경(adminToken, feedbackId, "REFLECTED").andExpect(status().isOk());

        상태_변경(adminToken, feedbackId, "REFLECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFLECTED"));
    }

    @Test
    void 일반_관리자도_반영_상태를_바꾼다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "일반 관리자가 처리할 의견");

        상태_변경(일반관리자_토큰(), feedbackId, "REFLECTED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFLECTED"));
    }

    @Test
    void 없는_의견의_상태를_바꾸면_404() throws Exception {
        상태_변경(슈퍼관리자_토큰(), -1L, "REFLECTED")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
    }

    @Test
    void 정해지지_않은_상태값이면_400() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "상태값 검증");
        String adminToken = 슈퍼관리자_토큰();

        상태_변경(adminToken, feedbackId, "DONE")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_STATUS"));
        상태_변경(adminToken, feedbackId, "reflected")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_STATUS"));
        상태_변경(adminToken, feedbackId, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_STATUS"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNREFLECTED"));
    }

    @Test
    void 관리자_토큰_없이_상태를_바꾸면_401() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "남의 의견을 처리하려는 시도");

        mockMvc.perform(patch("/api/v1/admin/feedbacks/{feedbackId}/status", feedbackId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REFLECTED\"}"))
            .andExpect(status().isUnauthorized());
        // 익명 사용자 토큰도 관리자 토큰이 아니라 토큰 단계에서 걸린다.
        상태_변경(memberToken, feedbackId, "REFLECTED").andExpect(status().isUnauthorized());

        단건_조회(슈퍼관리자_토큰(), feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNREFLECTED"));
    }
}
