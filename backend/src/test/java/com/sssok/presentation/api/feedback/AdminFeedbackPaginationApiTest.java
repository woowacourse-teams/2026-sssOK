package com.sssok.presentation.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.infrastructure.persistence.feedback.FeedbackJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// 커서 페이지네이션 인수 테스트 — 페이지를 이어 받는 동작과 size 경계, 그리고 의견이 동시에
// 등록돼 작성 시각과 ID 의 순서가 어긋난 경우를 본다. 조회 자체의 응답 계약은 AdminFeedbackApiTest 가 본다.
class AdminFeedbackPaginationApiTest extends AdminFeedbackApiSupport {

    @Test
    void 커서로_다음_페이지를_이어_받는다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long first = 의견_남기기(memberToken, roomId, "첫 번째");
        long second = 의견_남기기(memberToken, roomId, "두 번째");
        long third = 의견_남기기(memberToken, roomId, "세 번째");
        String adminToken = 슈퍼관리자_토큰();

        MvcResult page1 = 목록_조회(adminToken, null, 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(third))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.nextCursor").value(third))
            .andReturn();

        MvcResult page2 = 목록_조회(adminToken, 커서(page1), 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(second))
            .andExpect(jsonPath("$.data.nextCursor").value(second))
            .andReturn();

        목록_조회(adminToken, 커서(page2), 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(first));
    }

    @Test
    void 더_읽을_의견이_없으면_hasNext가_false고_nextCursor가_null이다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        의견_남기기(memberToken, roomId, "하나뿐인 의견");

        목록_조회(슈퍼관리자_토큰(), null, 20)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(1)))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").value(Matchers.nullValue()));
    }

    @Test
    void 의견이_하나도_없으면_빈_목록을_받는다() throws Exception {
        목록_조회(슈퍼관리자_토큰(), null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks").isEmpty())
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").value(Matchers.nullValue()));
    }

    @Test
    void size의_허용_경계인_1과_100은_통과한다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        의견_남기기(memberToken, roomId, "경계 확인용 의견");
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, null, 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(1)));

        목록_조회(adminToken, null, 100)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(1)));
    }

    @Test
    void size가_범위를_벗어나면_400() throws Exception {
        String adminToken = 슈퍼관리자_토큰();

        목록_조회(adminToken, null, 0)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));

        목록_조회(adminToken, null, 101)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void size가_숫자가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰())
                .param("size", "스무개"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    // 잘못 보낸 요청은 400 아니면 정상 처리여야 한다. 500 이 나면 서버 잘못으로 보이게 된다.
    @Test
    void 빈_값으로_보낸_cursor나_size는_생략한_것과_같게_처리된다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long feedbackId = 의견_남기기(memberToken, roomId, "빈 파라미터로 조회해도 되는 의견");

        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .header("Authorization", "Bearer " + 슈퍼관리자_토큰())
                .param("cursor", "")
                .param("size", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(1)))
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(feedbackId));
    }

    @Test
    void 없는_의견을_가리키는_cursor로_조회하면_400() throws Exception {
        목록_조회(슈퍼관리자_토큰(), -1L, 20)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void 작성_시각과_ID_순서가_어긋난_의견도_커서_순회에서_한_번씩만_나온다() throws Exception {
        // 작성 시각은 애플리케이션이, ID 는 DB 가 매긴다. 거의 동시에 등록된 두 의견에서는
        // 나중에 저장된(= ID 가 큰) 쪽의 작성 시각이 더 이를 수 있다. 그 상황을 직접 심는다.
        Instant base = Instant.now();
        long later = 의견_직접_저장("작성 시각이 나중인 의견", base);
        long earlier = 의견_직접_저장("작성 시각이 이른 의견", base.minusSeconds(1));

        List<Long> 순회 = 커서로_전부_순회(슈퍼관리자_토큰(), 1);

        // 정렬은 작성 시각 기준이라 later 가 먼저다. ID 만으로 커서를 자르면 earlier 가 통째로 빠진다.
        assertThat(순회).containsExactly(later, earlier);
    }

    @Test
    void 같은_시각에_등록된_의견도_커서_순회에서_한_번씩만_나온다() throws Exception {
        Instant sameMoment = Instant.now();
        long first = 의견_직접_저장("같은 시각 1", sameMoment);
        long second = 의견_직접_저장("같은 시각 2", sameMoment);
        long third = 의견_직접_저장("같은 시각 3", sameMoment);

        List<Long> 순회 = 커서로_전부_순회(슈퍼관리자_토큰(), 1);

        // 시각이 같을 때는 ID 내림차순이 보조 정렬 키다.
        assertThat(순회).containsExactly(third, second, first);
    }

    @Test
    void 동시에_등록된_의견도_커서로_순회하면_누락되거나_중복되지_않는다() throws Exception {
        int 회원수 = 8;
        String hostToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(hostToken);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 회원수; i++) {
            String token = 익명_인증("동시등록" + i);
            방_입장(token, roomId);
            tokens.add(token);
        }

        동시에_의견_등록(tokens, roomId);

        List<Long> 순회 = 커서로_전부_순회(슈퍼관리자_토큰(), 3);
        assertThat(순회).doesNotHaveDuplicates();
        assertThat(순회).hasSize(회원수);
        assertThat(순회).containsExactlyInAnyOrderElementsOf(
            feedbackJpaRepository.findAll().stream().map(FeedbackJpaEntity::getId).toList());
    }

    @Test
    void 페이지를_넘기는_중에_새_의견이_들어와도_이미_받은_페이지가_밀리지_않는다() throws Exception {
        String memberToken = 익명_인증("가현");
        long roomId = 방_만들고_입장(memberToken);
        long first = 의견_남기기(memberToken, roomId, "첫 번째");
        long second = 의견_남기기(memberToken, roomId, "두 번째");
        String adminToken = 슈퍼관리자_토큰();

        MvcResult page1 = 목록_조회(adminToken, null, 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(second))
            .andReturn();

        // 커서를 받아 둔 뒤 새 의견이 들어온다. 커서 기반이라 새 의견은 다음 페이지에 끼어들지 않는다.
        의견_남기기(memberToken, roomId, "조회 중에 들어온 의견");

        목록_조회(adminToken, 커서(page1), 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.feedbacks", Matchers.hasSize(1)))
            .andExpect(jsonPath("$.data.feedbacks[0].feedbackId").value(first));
    }

    // 동시 등록 상황을 만든다. 모든 스레드를 같은 시점에 풀어 한꺼번에 저장되게 한다.
    private void 동시에_의견_등록(List<String> tokens, long roomId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tokens.size());
        CountDownLatch 출발 = new CountDownLatch(1);
        CountDownLatch 완료 = new CountDownLatch(tokens.size());
        AtomicInteger 성공 = new AtomicInteger();
        for (String token : tokens) {
            executor.submit(() -> {
                try {
                    출발.await();
                    mockMvc.perform(post("/api/v1/rooms/{roomId}/feedbacks", roomId)
                            .header("Authorization", "Bearer " + token)
                            .header(HttpHeaders.USER_AGENT, CHROME_UA)
                            .header("X-App-Version", FRONTEND_VERSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"동시에 남긴 의견\"}"))
                        .andExpect(status().isCreated());
                    성공.incrementAndGet();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    완료.countDown();
                }
            });
        }
        출발.countDown();
        assertThat(완료.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(성공.get()).isEqualTo(tokens.size());
    }

    // 커서를 따라 끝까지 읽어 나온 순서대로 ID 를 모은다.
    private List<Long> 커서로_전부_순회(String adminToken, int size) throws Exception {
        List<Long> ids = new ArrayList<>();
        Long cursor = null;
        while (true) {
            MvcResult result = 목록_조회(adminToken, cursor, size)
                .andExpect(status().isOk())
                .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
            data.get("feedbacks").forEach(feedback -> ids.add(feedback.get("feedbackId").asLong()));
            if (!data.get("hasNext").asBoolean()) {
                return ids;
            }
            cursor = data.get("nextCursor").asLong();
        }
    }

    // 작성 시각을 마음대로 지정해야 하는 경우에만 쓴다. 등록 API 는 시각을 서버가 정한다.
    private long 의견_직접_저장(String content, Instant createdAt) {
        return feedbackJpaRepository.save(new FeedbackJpaEntity(
            null, content, 1L, "우테코 회식", 1L, "가현", CHROME_UA, FRONTEND_VERSION, createdAt)).getId();
    }

    private long 커서(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("nextCursor").asLong();
    }
}
