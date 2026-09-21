package com.sssok.presentation.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

// API 인수 테스트 — 의견을 남긴 뒤 관리자 토큰으로 목록·단건 조회를 관통 확인한다.
// 커서 페이지네이션과 정렬은 AdminFeedbackPaginationApiTest 가 따로 본다.
class AdminFeedbackApiTest extends AdminFeedbackApiSupport {

    private static final AtomicInteger REVOKED_SEQUENCE = new AtomicInteger();

    @Test
    void 관리자는_의견_목록을_최신순으로_조회한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long older = 의견_남기기(memberToken, roomId, "먼저 남긴 의견");
        long newer = 의견_남기기(memberToken, roomId, "나중에 남긴 의견");

        목록_조회(슈퍼관리자_토큰(), null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(newer))
            .andExpect(jsonPath("$.data.feedbacks[0].content").value("나중에 남긴 의견"))
            .andExpect(jsonPath("$.data.feedbacks[1].feedbackId").value(older));
    }

    @Test
    void 목록_항목에는_방_이름과_닉네임과_작성_시각이_함께_담긴다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "맥락이 같이 보여야 해요");

        목록_조회(슈퍼관리자_토큰(), null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.feedbacks[0].roomId").value(roomId))
            .andExpect(jsonPath("$.data.feedbacks[0].roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.feedbacks[0].memberId").value(Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.feedbacks[0].nickname").value("가현"))
            .andExpect(jsonPath("$.data.feedbacks[0].createdAt").value(Matchers.notNullValue()));
    }

    @Test
    void 목록의_본문은_잘리고_단건은_전문으로_내려온다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        String longContent = "가".repeat(150);
        long feedbackId = 의견_남기기(memberToken, roomId, longContent);
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].content").value("가".repeat(100) + "..."));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").value(longContent));
    }

    @Test
    void 관리자는_의견_단건의_전문과_맥락을_조회한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "진행률이 멈춘 것처럼 보여요");

        단건_조회(슈퍼관리자_토큰(), feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.content").value("진행률이 멈춘 것처럼 보여요"))
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.memberId").value(Matchers.notNullValue()))
            .andExpect(jsonPath("$.data.nickname").value("가현"))
            .andExpect(jsonPath("$.data.createdAt").value(Matchers.notNullValue()));
    }

    @Test
    void 작성_시각은_ISO_8601_문자열로_내려온다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "시각 형식 확인");
        String adminToken = 슈퍼관리자_토큰();

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createdAt")
                .value(Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z")));

        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].createdAt")
                .value(Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z")));
    }

    @Test
    void 응답에는_명세에_적힌_필드만_담긴다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "필드 목록 확인");
        String adminToken = 슈퍼관리자_토큰();

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.*", Matchers.hasSize(9)))
            .andExpect(jsonPath("$.data.feedbackId").exists())
            .andExpect(jsonPath("$.data.content").exists())
            .andExpect(jsonPath("$.data.roomId").exists())
            .andExpect(jsonPath("$.data.roomName").exists())
            .andExpect(jsonPath("$.data.memberId").exists())
            .andExpect(jsonPath("$.data.nickname").exists())
            .andExpect(jsonPath("$.data.userAgent").exists())
            .andExpect(jsonPath("$.data.frontendVersion").exists())
            .andExpect(jsonPath("$.data.createdAt").exists());

        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.*", Matchers.hasSize(3)))
            .andExpect(jsonPath("$.data.feedbacks[0].*", Matchers.hasSize(9)));
    }

    @Test
    void 없는_의견을_조회하면_404() throws Exception {
        단건_조회(슈퍼관리자_토큰(), -1L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
    }

    @Test
    void 의견_식별자가_숫자가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", "없는것")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void 저장된_userAgent와_frontendVersion이_그대로_내려온다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "아이폰에서 썸네일이 늦게 떠요");
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].userAgent").value(CHROME_UA))
            .andExpect(jsonPath("$.data.feedbacks[0].frontendVersion").value(FRONTEND_VERSION));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userAgent").value(CHROME_UA))
            .andExpect(jsonPath("$.data.frontendVersion").value(FRONTEND_VERSION));
    }

    @Test
    void 헤더_없이_저장된_의견도_조회되고_userAgent와_frontendVersion이_null이다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "curl 로 보냈어요", null, null);
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.feedbacks[0].userAgent").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.data.feedbacks[0].frontendVersion").value(Matchers.nullValue()));

        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userAgent").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.data.frontendVersion").value(Matchers.nullValue()));
    }

    @Test
    void 방을_purge_해도_의견이_조회되고_맥락_값이_보존된다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "방이 사라져도 이 의견은 읽혀야 해요");
        Long memberId = feedbackJpaRepository.findById(feedbackId).orElseThrow().getMemberId();

        방_삭제(memberToken, roomId).andExpect(status().isOk());
        보존_기간이_지난_삭제로_되돌리기(roomId);
        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomRepository.findById(roomId)).isEmpty();
        assertThat(memberRepository.findById(memberId)).isEmpty();

        String adminToken = 슈퍼관리자_토큰();
        단건_조회(adminToken, feedbackId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").value("방이 사라져도 이 의견은 읽혀야 해요"))
            .andExpect(jsonPath("$.data.roomId").value(roomId))
            .andExpect(jsonPath("$.data.roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.memberId").value(memberId))
            .andExpect(jsonPath("$.data.nickname").value("가현"));

        목록_조회(adminToken, null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId))
            .andExpect(jsonPath("$.data.feedbacks[0].roomName").value("우테코 회식"))
            .andExpect(jsonPath("$.data.feedbacks[0].nickname").value("가현"));
    }

    @Test
    void 일반_관리자도_의견을_조회한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "일반 관리자도 읽어야 해요");
        String adminToken = 일반관리자_토큰();

        목록_조회(adminToken, null, 20).andExpect(status().isOk());
        단건_조회(adminToken, feedbackId).andExpect(status().isOk());
    }

    @Test
    void 익명_사용자_토큰으로_조회하면_거부된다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "남의 의견을 보려는 시도");

        // 관리자 토큰이 아니라 토큰 단계에서 걸려 401 이 난다.
        목록_조회(memberToken, null, 20).andExpect(status().isUnauthorized());
        단건_조회(memberToken, feedbackId).andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 삭제된_관리자의_토큰으로_조회하면_403() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "권한이 사라진 뒤에는 못 읽어야 해요");
        String superToken = 슈퍼관리자_토큰();
        String loginId = "revokedreader" + REVOKED_SEQUENCE.getAndIncrement();
        String revokedToken = 일반관리자_토큰(loginId);
        long revokedId = adminJpaRepository.findByLoginId(loginId).orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/admin/accounts/{adminId}", revokedId)
            .header("Authorization", "Bearer " + superToken)).andExpect(status().isOk());

        // 토큰은 아직 만료되지 않았지만 권한 판정은 매 요청마다 계정을 다시 읽는다.
        목록_조회(revokedToken, null, 20)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
        단건_조회(revokedToken, feedbackId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_FORBIDDEN"));
    }
}
