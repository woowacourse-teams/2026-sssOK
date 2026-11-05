package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.application.organization.interpret.OrganizeInterpretation;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Repository + Service 통합 테스트 (H2). 접수 검증·방 활성 제한·멱등 키·회원 유형별 사용 제한을 확인한다.
@OrganizeH2Test
class OrganizeStartServiceTest extends OrganizeServiceTestSupport {
    private ErrorCode codeOf(Runnable action) {
        return Assertions.assertThrows(SssOkException.class, action::run).errorCode();
    }

    @Nested
    class 접수 {

        @Test
        void 버튼으로_접수하면_QUEUED_작업과_실행_대상_사진이_한_번에_저장된다() {
            Long host = registeredMember();
            Long room = room(host);
            List<Long> ids = jpegs(room, host, 3);
            photo(room, host, "movie.mp4", "video/mp4", true);
            photo(room, host, "busy.jpg", "image/jpeg", false);

            OrganizeJob job = startService.startByKind(room, host, OrganizeKind.SIMILAR, key());

            assertThat(job.status()).isEqualTo(OrganizeJobStatus.QUEUED);
            assertThat(job.kind()).isEqualTo(OrganizeKind.SIMILAR);
            assertThat(job.trigger()).isEqualTo(OrganizeTrigger.BUTTON);
            assertThat(job.photoCount()).isEqualTo(3);
            assertThat(job.excludedCount()).isEqualTo(2);
            assertThat(job.jobVersion()).isEqualTo(1);
            assertThat(job.toolName()).isEqualTo("group_similar_photos");
            assertThat(repository.findPhotos(job.id())).extracting(photo -> photo.mediaId())
                .containsExactlyElementsOf(ids);
            assertThat(repository.findLatestExecution(job.id())).get()
                .extracting(execution -> execution.attempt()).isEqualTo(1);
            assertThat(repository.findRoomState(room)).get()
                .extracting("busy", "version").containsExactly(true, 1L);
        }

        @Test
        void 문장으로_접수하면_모델이_고른_도구의_종류로_작업을_만든다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            given(interpreter.interpret("사람별로 모아줘")).willReturn(new OrganizeInterpretation.ToolSelected(PERSON_TOOL));

            OrganizeJob job = startService.startByText(room, host, "  사람별로 모아줘 ", key());

            assertThat(job.kind()).isEqualTo(OrganizeKind.PERSON);
            assertThat(job.trigger()).isEqualTo(OrganizeTrigger.TEXT);
            assertThat(job.requestText()).isEqualTo("사람별로 모아줘");
        }

        @Test
        void 되묻기와_미지원은_작업을_만들지_않는다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            given(interpreter.interpret("음...")).willReturn(new OrganizeInterpretation.NeedsClarification());
            given(interpreter.interpret("지워줘")).willReturn(new OrganizeInterpretation.Unsupported());

            assertThat(codeOf(() -> startService.startByText(room, host, "음...", key())))
                .isEqualTo(ErrorCode.ORGANIZE_NEEDS_CLARIFICATION);
            assertThat(codeOf(() -> startService.startByText(room, host, "지워줘", key())))
                .isEqualTo(ErrorCode.ORGANIZE_UNSUPPORTED_REQUEST);
            assertThat(repository.findActiveByRoom(room)).isEmpty();
        }

        @Test
        void 문장은_비었거나_1000자를_넘으면_거절한다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);

            assertThat(codeOf(() -> startService.startByText(room, host, "  ", key()))).isEqualTo(ErrorCode.INVALID_PARAM);
            assertThat(codeOf(() -> startService.startByText(room, host, "가".repeat(1001), key())))
                .isEqualTo(ErrorCode.INVALID_PARAM);
        }

        @Test
        void 분석할_사진이_없거나_한도를_넘으면_임의로_일부만_고르지_않고_거절한다() {
            Long host = registeredMember();
            Long room = room(host);
            photo(room, host, "movie.mp4", "video/mp4", true);
            assertThat(codeOf(() -> startPerson(room, host))).isEqualTo(ErrorCode.ORGANIZE_NO_ANALYZABLE_PHOTOS);

            jpegs(room, host, 6);
            assertThat(codeOf(() -> startPerson(room, host))).isEqualTo(ErrorCode.ORGANIZE_TOO_MANY_PHOTOS);
            assertThat(repository.findActiveByRoom(room)).isEmpty();
        }

        @Test
        void 방에_활성_작업이_있으면_거절하고_본인_작업일_때만_ID를_알린다() {
            Long host = registeredMember();
            Long guest = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            OrganizeJob first = startPerson(room, host);
            // 접수 간격(60초)이 지난 뒤여야 방 활성 제한이 먼저 드러난다.
            clock.advance(Duration.ofSeconds(61));

            assertThatThrownBy(() -> startPerson(room, host)).isInstanceOfSatisfying(
                OrganizeJobInProgressException.class, e -> assertThat(e.activeJobId()).isEqualTo(first.publicId()));
            assertThatThrownBy(() -> startPerson(room, guest)).isInstanceOfSatisfying(
                OrganizeJobInProgressException.class, e -> assertThat(e.activeJobId()).isNull());
        }

        @Test
        void 검토_기한이_지난_READY는_새_접수가_먼저_만료시키고_통과한다() {
            Long host = registeredMember();
            Long guest = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            OrganizeJob first = startPerson(room, host);
            states.ready(first);
            clock.advance(Duration.ofMinutes(31));

            OrganizeJob second = startService.startByKind(room, guest, OrganizeKind.SIMILAR, key());

            assertThat(reload(first).status()).isEqualTo(OrganizeJobStatus.EXPIRED);
            assertThat(second.status()).isEqualTo(OrganizeJobStatus.QUEUED);
        }
    }

    @Nested
    class 멱등_키 {

        @Test
        void 같은_키와_같은_입력이면_작업을_다시_만들지_않고_처음_작업을_돌려준다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            String key = key();

            OrganizeJob first = startService.startByKind(room, host, OrganizeKind.PERSON, key);
            OrganizeJob replay = startService.startByKind(room, host, OrganizeKind.PERSON, key);

            assertThat(replay.publicId()).isEqualTo(first.publicId());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_job WHERE room_id = ?", Long.class, room))
                .isEqualTo(1);
        }

        @Test
        void 같은_키에_다른_입력이면_거절한다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            String key = key();
            startService.startByKind(room, host, OrganizeKind.PERSON, key);

            assertThat(codeOf(() -> startService.startByKind(room, host, OrganizeKind.SIMILAR, key)))
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        void 키가_없거나_형식이_틀리면_거절한다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);

            for (String invalid : new String[] {null, "", "짧은키", "a".repeat(65), "has space in key!!"}) {
                assertThat(codeOf(() -> startService.startByKind(room, host, OrganizeKind.PERSON, invalid)))
                    .isEqualTo(ErrorCode.INVALID_PARAM);
            }
            assertThat(repository.findActiveByRoom(room)).isEmpty();
        }

        @Test
        void 실패한_요청은_기록하지_않아_같은_키로_다시_보낼_수_있다() {
            Long host = registeredMember();
            Long room = room(host);
            String key = key();
            assertThat(codeOf(() -> startService.startByKind(room, host, OrganizeKind.PERSON, key)))
                .isEqualTo(ErrorCode.ORGANIZE_NO_ANALYZABLE_PHOTOS);

            jpegs(room, host, 2);
            OrganizeJob job = startService.startByKind(room, host, OrganizeKind.PERSON, key);

            assertThat(job.status()).isEqualTo(OrganizeJobStatus.QUEUED);
        }
    }

    @Nested
    class 익명_체험 {

        @Test
        void 방마다_1회만_쓸_수_있다() {
            Long anonymous = anonymousMember();
            Long room = room(anonymous);
            jpegs(room, anonymous, 2);
            OrganizeJob job = startPerson(room, anonymous);
            states.cancelAfterAccepted(job, anonymous);

            assertThat(codeOf(() -> startPerson(room, anonymous))).isEqualTo(ErrorCode.ORGANIZE_TRIAL_EXHAUSTED);
        }

        @Test
        void 다른_방의_체험은_따로_쓴다() {
            Long anonymous = anonymousMember();
            Long first = room(anonymous);
            Long second = room(anonymous);
            jpegs(first, anonymous, 2);
            jpegs(second, anonymous, 2);

            startPerson(first, anonymous);

            assertThat(startPerson(second, anonymous).status()).isEqualTo(OrganizeJobStatus.QUEUED);
        }

        @Test
        void OAuth_회원은_횟수_제한이_없다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            for (int i = 0; i < 3; i++) {
                OrganizeJob job = startPerson(room, host);
                states.cancelAfterAccepted(job, host);
                clock.advance(Duration.ofSeconds(61));
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_job WHERE room_id = ?", Long.class, room))
                .isEqualTo(3);
        }
    }

    @Nested
    class 접수_간격 {

        @Test
        void OAuth_회원은_간격_안에_다시_접수할_수_없고_남은_초를_알린다() {
            Long host = registeredMember();
            Long first = room(host);
            Long second = room(host);
            jpegs(first, host, 2);
            jpegs(second, host, 2);
            startPerson(first, host);
            clock.advance(Duration.ofSeconds(20));

            assertThatThrownBy(() -> startPerson(second, host)).isInstanceOfSatisfying(
                OrganizeRateLimitedException.class,
                e -> assertThat(e.retryAfterSeconds()).isBetween(39L, 42L));
            assertThat(repository.findActiveByRoom(second)).isEmpty();
        }

        @Test
        void 간격이_지나면_다른_방에서도_접수할_수_있다() {
            Long host = registeredMember();
            Long first = room(host);
            Long second = room(host);
            jpegs(first, host, 2);
            jpegs(second, host, 2);
            startPerson(first, host);
            clock.advance(Duration.ofSeconds(61));

            assertThat(startPerson(second, host).status()).isEqualTo(OrganizeJobStatus.QUEUED);
        }

        @Test
        void 검증에서_거절된_요청은_간격을_시작하지_않는다() {
            Long host = registeredMember();
            Long room = room(host);
            assertThat(codeOf(() -> startPerson(room, host))).isEqualTo(ErrorCode.ORGANIZE_NO_ANALYZABLE_PHOTOS);
            assertThat(repository.lastStartedAt(host)).isEmpty();

            jpegs(room, host, 2);
            assertThat(startPerson(room, host).status()).isEqualTo(OrganizeJobStatus.QUEUED);
        }

        @Test
        void 간격은_회원_기준이라_다른_회원은_영향이_없다() {
            Long host = registeredMember();
            Long guest = registeredMember();
            Long first = room(host);
            Long second = room(guest);
            jpegs(first, host, 2);
            jpegs(second, guest, 2);
            startPerson(first, host);

            assertThat(startPerson(second, guest).status()).isEqualTo(OrganizeJobStatus.QUEUED);
        }
    }

    @Nested
    class 문장_해석_횟수 {

        @Test
        void 해석을_시도한_요청은_회원당_분당_5회까지다() {
            Long host = registeredMember();
            Long room = room(host);
            jpegs(room, host, 2);
            given(interpreter.interpret("음...")).willReturn(new OrganizeInterpretation.NeedsClarification());

            for (int i = 0; i < 5; i++) {
                assertThat(codeOf(() -> startService.startByText(room, host, "음...", key())))
                    .isEqualTo(ErrorCode.ORGANIZE_NEEDS_CLARIFICATION);
            }
            assertThatThrownBy(() -> startService.startByText(room, host, "음...", key()))
                .isInstanceOf(OrganizeRateLimitedException.class);
        }

        @Test
        void 모델을_부르기_전에_거절된_요청은_횟수에_세지_않는다() {
            Long host = registeredMember();
            Long room = room(host);
            given(interpreter.interpret("음...")).willReturn(new OrganizeInterpretation.NeedsClarification());

            for (int i = 0; i < 6; i++) {
                assertThat(codeOf(() -> startService.startByText(room, host, "음...", key())))
                    .isEqualTo(ErrorCode.ORGANIZE_NO_ANALYZABLE_PHOTOS);
            }
        }
    }
}
