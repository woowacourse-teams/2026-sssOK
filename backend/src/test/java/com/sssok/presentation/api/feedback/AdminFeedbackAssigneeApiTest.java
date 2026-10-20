package com.sssok.presentation.api.feedback;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminFeedbackAssigneeApiTest extends AdminFeedbackApiSupport {

    @Test
    void 새로_남긴_의견은_담당자가_없다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "방금 남긴 의견");
        String adminToken = 슈퍼관리자_토큰();

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value(nullValue()));
        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].assignee").value(nullValue()));
    }

    @Test
    void 담당자를_정하면_다시_조회해도_그대로_보인다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "썸네일이 늦게 떠요");
        String adminToken = 슈퍼관리자_토큰();

        담당자_변경(adminToken, feedbackId, "YUNDOL")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.content").value("썸네일이 늦게 떠요"))
            .andExpect(jsonPath("$.data.assignee").value("YUNDOL"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("YUNDOL"));
        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].assignee").value("YUNDOL"));
    }

    @Test
    void 담당자를_다른_사람으로_바꾼다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "넘겨줄 의견");
        String adminToken = 슈퍼관리자_토큰();
        담당자_변경(adminToken, feedbackId, "MAICHAN").andExpect(status().isOk());

        담당자_변경(adminToken, feedbackId, "HYUNMIBAP")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("HYUNMIBAP"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("HYUNMIBAP"));
    }

    @Test
    void null_을_보내면_담당자를_비운다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "담당 해제할 의견");
        String adminToken = 슈퍼관리자_토큰();
        담당자_변경(adminToken, feedbackId, "HAENI").andExpect(status().isOk());

        담당자_변경(adminToken, feedbackId, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value(nullValue()));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value(nullValue()));
    }

    @Test
    void 담당자와_처리_상태는_서로를_덮어쓰지_않는다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "둘 다 바꿀 의견");
        String adminToken = 슈퍼관리자_토큰();

        담당자_변경(adminToken, feedbackId, "YUNDOL").andExpect(status().isOk());
        상태_변경(adminToken, feedbackId, "IN_PROGRESS")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("YUNDOL"));
        담당자_변경(adminToken, feedbackId, "MAICHAN")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.assignee").value("MAICHAN"));
    }

    @Test
    void 일반_관리자도_담당자를_바꾼다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "일반 관리자가 맡을 의견");

        담당자_변경(일반관리자_토큰(), feedbackId, "HAENI")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("HAENI"));
    }

    @Test
    void 없는_의견의_담당자를_바꾸면_404() throws Exception {
        담당자_변경(슈퍼관리자_토큰(), -1L, "YUNDOL")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
    }

    @Test
    void 정해지지_않은_담당자면_400() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "담당자 검증");
        String adminToken = 슈퍼관리자_토큰();
        담당자_변경(adminToken, feedbackId, "YUNDOL").andExpect(status().isOk());

        담당자_변경(adminToken, feedbackId, "GAHYEON")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_ASSIGNEE"));
        담당자_변경(adminToken, feedbackId, "yundol")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_ASSIGNEE"));
        담당자_변경(adminToken, feedbackId, "")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_ASSIGNEE"));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value("YUNDOL"));
    }

    @Test
    void 관리자_토큰_없이_담당자를_바꾸면_401() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "남의 의견을 맡으려는 시도");

        mockMvc.perform(patch("/api/v1/admin/feedbacks/{feedbackId}/assignee", feedbackId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"YUNDOL\"}"))
            .andExpect(status().isUnauthorized());
        담당자_변경(memberToken, feedbackId, "YUNDOL").andExpect(status().isUnauthorized());

        단건_조회(슈퍼관리자_토큰(), feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignee").value(nullValue()));
    }
}
