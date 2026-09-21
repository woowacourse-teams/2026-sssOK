package com.sssok.application.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.admin.exception.AdminForbiddenException;
import com.sssok.application.feedback.exception.FeedbackNotFoundException;
import com.sssok.application.feedback.exception.InvalidFeedbackCursorException;
import com.sssok.application.feedback.exception.InvalidFeedbackPageSizeException;
import com.sssok.application.port.out.AdminRepository;
import com.sssok.application.port.out.FeedbackRepository;
import com.sssok.domain.admin.Admin;
import com.sssok.domain.admin.AdminName;
import com.sssok.domain.admin.AdminRole;
import com.sssok.domain.admin.LoginId;
import com.sssok.domain.feedback.Feedback;
import com.sssok.domain.feedback.FeedbackContent;
import com.sssok.domain.feedback.FrontendVersion;
import com.sssok.domain.feedback.UserAgent;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Repository + Service 통합 테스트 (H2). 관리자 토큰 파싱은 표현 계층의 일이라 여기서는
// 이미 확인된 adminId 를 넘겨 권한 판정부터 본다.
@SpringBootTest
@ActiveProfiles("test")
class FeedbackQueryServiceTest {

    private static final Long ROOM_ID = 900L;
    private static final String ROOM_NAME = "우테코 회식";
    private static final Long MEMBER_ID = 901L;
    private static final String NICKNAME = "가현";

    private static final AtomicInteger LOGIN_ID_SEQUENCE = new AtomicInteger();

    @Autowired
    FeedbackQueryService feedbackQueryService;

    @Autowired
    FeedbackRepository feedbackRepository;

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long adminId;

    // 이 테스트는 롤백되지 않으므로 의견을 직접 비운다. 목록 조회는 테이블 전체를 읽어서,
    // 남은 의견이 있으면 "몇 번째 항목" 검증이 실행 순서에 따라 흔들린다.
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM feedback");
        adminId = 관리자_등록(AdminRole.ADMIN);
    }

    @Test
    void 최신순으로_내려준다() {
        Instant now = Instant.now();
        Feedback older = 의견_저장("먼저 남긴 의견", now.minusSeconds(60));
        Feedback newer = 의견_저장("나중에 남긴 의견", now);

        FeedbackPage page = feedbackQueryService.findLatest(adminId, null, null);

        assertThat(page.feedbacks()).extracting(Feedback::getId)
            .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void size_만큼만_내려주고_다음_커서를_채운다() {
        Instant now = Instant.now();
        의견_저장("첫 번째", now.minusSeconds(60));
        Feedback newer = 의견_저장("두 번째", now);

        FeedbackPage page = feedbackQueryService.findLatest(adminId, null, 1);

        assertThat(page.feedbacks()).hasSize(1);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(newer.getId());
    }

    @Test
    void 마지막_페이지는_hasNext가_false고_nextCursor가_null이다() {
        의견_저장("하나뿐인 의견", Instant.now());

        FeedbackPage page = feedbackQueryService.findLatest(adminId, null, 20);

        assertThat(page.feedbacks()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 의견이_없으면_빈_페이지를_준다() {
        FeedbackPage page = feedbackQueryService.findLatest(adminId, null, 20);

        assertThat(page.feedbacks()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 커서_다음부터_이어_읽는다() {
        Instant now = Instant.now();
        Feedback oldest = 의견_저장("첫 번째", now.minusSeconds(120));
        Feedback middle = 의견_저장("두 번째", now.minusSeconds(60));
        Feedback newest = 의견_저장("세 번째", now);

        FeedbackPage first = feedbackQueryService.findLatest(adminId, null, 1);
        FeedbackPage second = feedbackQueryService.findLatest(adminId, first.nextCursor(), 1);
        FeedbackPage third = feedbackQueryService.findLatest(adminId, second.nextCursor(), 1);

        assertThat(first.feedbacks()).extracting(Feedback::getId).containsExactly(newest.getId());
        assertThat(second.feedbacks()).extracting(Feedback::getId).containsExactly(middle.getId());
        assertThat(third.feedbacks()).extracting(Feedback::getId).containsExactly(oldest.getId());
    }

    @Test
    void 작성_시각과_ID_순서가_어긋나도_커서가_의견을_건너뛰지_않는다() {
        // 작성 시각은 애플리케이션이, ID 는 DB 가 매긴다. 거의 동시에 등록된 두 의견에서는
        // 나중에 저장된(= ID 가 큰) 쪽의 작성 시각이 더 이를 수 있다.
        Instant now = Instant.now();
        Feedback later = 의견_저장("작성 시각이 나중인 의견", now);
        Feedback earlier = 의견_저장("작성 시각이 이른 의견", now.minusSeconds(1));

        FeedbackPage first = feedbackQueryService.findLatest(adminId, null, 1);
        FeedbackPage second = feedbackQueryService.findLatest(adminId, first.nextCursor(), 1);

        // ID 만으로 잘랐다면 earlier(ID 가 더 큼)가 통째로 빠진다.
        assertThat(first.feedbacks()).extracting(Feedback::getId).containsExactly(later.getId());
        assertThat(second.feedbacks()).extracting(Feedback::getId).containsExactly(earlier.getId());
    }

    @Test
    void 작성_시각이_같으면_ID_내림차순으로_이어_읽는다() {
        Instant sameMoment = Instant.now();
        Feedback first = 의견_저장("같은 시각 1", sameMoment);
        Feedback second = 의견_저장("같은 시각 2", sameMoment);

        FeedbackPage page = feedbackQueryService.findLatest(adminId, null, 1);
        FeedbackPage next = feedbackQueryService.findLatest(adminId, page.nextCursor(), 1);

        assertThat(page.feedbacks()).extracting(Feedback::getId).containsExactly(second.getId());
        assertThat(next.feedbacks()).extracting(Feedback::getId).containsExactly(first.getId());
    }

    @Test
    void size를_생략하면_기본값으로_읽는다() {
        for (int i = 0; i <= FeedbackQueryService.DEFAULT_PAGE_SIZE; i++) {
            의견_저장("의견 " + i, Instant.now().minusSeconds(i));
        }

        FeedbackPage page = feedbackQueryService.findLatest(adminId, null, null);

        assertThat(page.feedbacks()).hasSize(FeedbackQueryService.DEFAULT_PAGE_SIZE);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void size가_범위를_벗어나면_예외() {
        assertThatThrownBy(() -> feedbackQueryService.findLatest(adminId, null, 0))
            .isInstanceOf(InvalidFeedbackPageSizeException.class);
        assertThatThrownBy(() ->
            feedbackQueryService.findLatest(adminId, null, FeedbackQueryService.MAX_PAGE_SIZE + 1))
            .isInstanceOf(InvalidFeedbackPageSizeException.class);
    }

    @Test
    void 없는_의견을_가리키는_커서면_예외() {
        assertThatThrownBy(() -> feedbackQueryService.findLatest(adminId, -1L, 20))
            .isInstanceOf(InvalidFeedbackCursorException.class);
    }

    @Test
    void 단건은_전문을_그대로_준다() {
        String content = "가".repeat(150);
        Feedback saved = 의견_저장(content, Instant.now());

        Feedback found = feedbackQueryService.findById(adminId, saved.getId());

        assertThat(found.getContent().value()).isEqualTo(content);
        assertThat(found.getRoomName()).isEqualTo(ROOM_NAME);
        assertThat(found.getNickname()).isEqualTo(NICKNAME);
    }

    @Test
    void 없는_의견을_조회하면_예외() {
        assertThatThrownBy(() -> feedbackQueryService.findById(adminId, -1L))
            .isInstanceOf(FeedbackNotFoundException.class);
    }

    @Test
    void 슈퍼관리자도_조회할_수_있다() {
        Long superAdminId = 관리자_등록(AdminRole.SUPER_ADMIN);
        Feedback saved = 의견_저장("슈퍼관리자도 읽는다", Instant.now());

        assertThat(feedbackQueryService.findLatest(superAdminId, null, 20).feedbacks())
            .extracting(Feedback::getId)
            .containsExactly(saved.getId());
        assertThat(feedbackQueryService.findById(superAdminId, saved.getId()).getId())
            .isEqualTo(saved.getId());
    }

    @Test
    void 계정이_없는_주체는_조회할_수_없다() {
        Feedback saved = 의견_저장("권한 없는 주체는 못 읽는다", Instant.now());

        assertThatThrownBy(() -> feedbackQueryService.findLatest(-1L, null, 20))
            .isInstanceOf(AdminForbiddenException.class);
        assertThatThrownBy(() -> feedbackQueryService.findById(-1L, saved.getId()))
            .isInstanceOf(AdminForbiddenException.class);
    }

    private Feedback 의견_저장(String content, Instant createdAt) {
        return feedbackRepository.save(Feedback.write(
            new FeedbackContent(content),
            ROOM_ID,
            ROOM_NAME,
            MEMBER_ID,
            NICKNAME,
            UserAgent.from(null),
            FrontendVersion.from(null),
            createdAt
        ));
    }

    private Long 관리자_등록(AdminRole role) {
        String loginId = "queryadmin" + LOGIN_ID_SEQUENCE.getAndIncrement();
        return adminRepository.save(Admin.register(
            new LoginId(loginId), "hashed", new AdminName("조회담당"), role, Instant.now())).getId();
    }
}
