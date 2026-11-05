package com.sssok.renewal.outgoing.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.support.H2IntegrationTest;
import com.sssok.renewal.application.organization.MutableClock;
import com.sssok.renewal.domain.organization.OrganizePhotoSource;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobDraft;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTargetPhoto;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// Repository 통합 테스트 (H2). 상태 변경이 모두 조건부 UPDATE 이고, 유니크 제약이 동시성을 DB 가 막아 주는지 확인한다.
// 서비스가 쓰는 규칙은 서비스 테스트에서, 여기서는 쿼리 하나하나의 조건과 제약만 본다.
@H2IntegrationTest
class OrganizeJobRepositoryTest {
    // 외부 FK가 없는 이력 식별자를 사용해 접수 서비스 없이 저장소 자체를 검증한다.
    private static final AtomicLong IDS = new AtomicLong(1_000_000);
    private final MutableClock clock = new MutableClock();
    @Autowired
    private OrganizeJobRepository repository;

    private Long member;
    private Long room;
    private OrganizeJob job;
    private Instant now;

    @BeforeEach
    void setUp() {
        member = nextId();
        room = nextId();
        job = createJob(room, member);
        now = clock.instant();
    }

    private Long nextId() {
        return IDS.incrementAndGet();
    }

    private OrganizeJob createJob(Long roomId, Long memberId) {
        Instant at = clock.instant();
        OrganizeJobDraft draft = new OrganizeJobDraft(UUID.randomUUID(), roomId, memberId,
            OrganizeKind.PERSON, OrganizeTrigger.BUTTON, null, "gallery", "group_photos_by_person", "설명", 2, 0);
        return repository.create(draft, UUID.randomUUID(), List.of(
            new OrganizeTargetPhoto(nextId(), "jpg", OrganizePhotoSource.PREVIEW),
            new OrganizeTargetPhoto(nextId(), "jpg", OrganizePhotoSource.PREVIEW)), at, at.plusSeconds(60));
    }

    private OrganizeJob reload(OrganizeJob target) {
        return repository.findById(target.id()).orElseThrow();
    }

    private OrganizeJobDraft draft(Long roomId, Long memberId) {
        return new OrganizeJobDraft(UUID.randomUUID(), roomId, memberId, OrganizeKind.PERSON, OrganizeTrigger.BUTTON,
            null, "gallery", "group_photos_by_person", "설명", 1, 0);
    }

    private OrganizeJob ready() {
        repository.markJobReady(job.id(), 1, "{}", now.plus(Duration.ofMinutes(30)), now.plus(Duration.ofDays(3)), now);
        return reload(job);
    }

    @Nested
    class 저장 {

        @Test
        void 작업과_첫_실행과_대상_사진을_함께_저장한다() {
            assertThat(job.status()).isEqualTo(OrganizeJobStatus.QUEUED);
            assertThat(job.jobVersion()).isEqualTo(1);
            assertThat(job.retryCount()).isZero();
            OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();
            assertThat(execution.attempt()).isEqualTo(1);
            assertThat(execution.status()).isEqualTo(OrganizeExecutionStatus.DISPATCH_PENDING);
            assertThat(repository.findPhotos(job.id())).extracting(OrganizeTargetPhoto::extension).containsOnly("jpg");
            assertThat(repository.findPhotos(job.id())).hasSize(2);
        }

        @Test
        void 방당_활성_작업은_DB_유니크가_하나로_막는다() {
            Long other = nextId();
            assertThatThrownBy(() -> repository.create(draft(room, other), UUID.randomUUID(), List.of(), now,
                now.plusSeconds(60))).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 같은_작업에_같은_회차의_실행을_둘_만들_수_없다() {
            assertThatThrownBy(() -> repository.addExecution(job.id(), 1, UUID.randomUUID(), now, now.plusSeconds(60)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class 상태_변경은_조건부다 {

        @Test
        void READY는_방을_놓지_않고_두_기한을_정하며_버전을_올린다() {
            assertThat(repository.markJobReady(job.id(), 1, "{}", now.plusSeconds(1800), now.plusSeconds(259200), now))
                .isTrue();

            OrganizeJob ready = reload(job);
            assertThat(ready.status()).isEqualTo(OrganizeJobStatus.READY);
            assertThat(ready.reviewExpiresAt()).isNotNull();
            assertThat(ready.resultExpiresAt()).isAfter(ready.reviewExpiresAt());
            assertThat(ready.jobVersion()).isEqualTo(job.jobVersion() + 1);
            assertThat(repository.findActiveByRoom(room)).isPresent();
        }

        @Test
        void READY나_닫힌_작업을_다시_READY로_만들지_않는다() {
            ready();
            assertThat(repository.markJobReady(job.id(), 1, "{}", now, now, now)).isFalse();

            repository.markJobCanceled(job.id(), now);
            assertThat(repository.markJobReady(job.id(), 1, "{}", now, now, now)).isFalse();
        }

        @Test
        void 실패는_진행_중인_작업만_닫고_방을_놓는다() {
            assertThat(repository.markJobFailed(job.id(), OrganizeFailureCode.LOST, "x", now)).isTrue();
            assertThat(reload(job).finishedAt()).isNotNull();
            assertThat(repository.findActiveByRoom(room)).isEmpty();

            assertThat(repository.markJobFailed(job.id(), OrganizeFailureCode.TIMEOUT, "y", now)).isFalse();
            assertThat(reload(job).failureCode()).isEqualTo("LOST");
        }

        @Test
        void 실패_상세는_500자로_자른다() {
            repository.markJobFailed(job.id(), OrganizeFailureCode.ANALYSIS_FAILED, "가".repeat(700), now);

            assertThat(reload(job).failureDetail()).hasSize(500);
        }

        @Test
        void 취소는_진행_중이거나_검토_중일_때만_되고_결과를_버린다() {
            ready();
            assertThat(repository.markJobCanceled(job.id(), now)).isTrue();
            assertThat(reload(job).hasResult()).isFalse();
            assertThat(repository.markJobCanceled(job.id(), now)).isFalse();
        }

        @Test
        void 반영은_기한_안의_READY만_되고_결과는_남긴다() {
            ready();
            assertThat(repository.markJobApplied(job.id(), now.plus(Duration.ofMinutes(31)))).isFalse();

            assertThat(repository.markJobApplied(job.id(), now.plus(Duration.ofMinutes(29)))).isTrue();

            OrganizeJob applied = reload(job);
            assertThat(applied.status()).isEqualTo(OrganizeJobStatus.APPLIED);
            assertThat(applied.hasResult()).isTrue();
            assertThat(repository.findActiveByRoom(room)).isEmpty();
        }

        @Test
        void 반영은_진행_중인_작업에는_되지_않는다() {
            assertThat(repository.markJobApplied(job.id(), now)).isFalse();
        }

        @Test
        void 만료는_기한이_지난_READY만_된다() {
            ready();
            assertThat(repository.markJobExpired(job.id(), now.plus(Duration.ofMinutes(10)))).isFalse();

            assertThat(repository.markJobExpired(job.id(), now.plus(Duration.ofMinutes(30)))).isTrue();

            assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.EXPIRED);
            assertThat(repository.findActiveByRoom(room)).isEmpty();
        }

        @Test
        void 기한이_지난_READY의_ID를_오래된_순서로_찾는다() {
            ready();

            assertThat(repository.findReviewOverdueIds(now, 10)).doesNotContain(job.id());
            assertThat(repository.findReviewOverdueIds(now.plus(Duration.ofMinutes(31)), 10)).contains(job.id());
        }

        @Test
        void 재시도는_실패한_작업만_QUEUED로_되돌리고_횟수를_올린다() {
            assertThat(repository.markJobRetried(job.id(), 1, now)).isFalse();
            repository.markJobFailed(job.id(), OrganizeFailureCode.ANALYSIS_FAILED, null, now);

            assertThat(repository.markJobRetried(job.id(), 1, now)).isTrue();

            OrganizeJob retried = reload(job);
            assertThat(retried.status()).isEqualTo(OrganizeJobStatus.QUEUED);
            assertThat(retried.retryCount()).isEqualTo(1);
            assertThat(retried.failureCode()).isNull();
            assertThat(retried.finishedAt()).isNull();
            assertThat(repository.findActiveByRoom(room)).isPresent();
        }

        @Test
        void 다른_작업이_방을_차지하고_있으면_재시도가_DB_제약에_막힌다() {
            repository.markJobFailed(job.id(), OrganizeFailureCode.ANALYSIS_FAILED, null, now);
            createJob(room, nextId());

            assertThatThrownBy(() -> repository.markJobRetried(job.id(), 1, now))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 결과_본문은_보관_기한이_지난_것만_비운다() {
            ready();

            assertThat(repository.purgeExpiredResults(now.plus(Duration.ofDays(2)))).isZero();
            assertThat(repository.purgeExpiredResults(now.plus(Duration.ofDays(3)).plusSeconds(1))).isPositive();

            assertThat(reload(job).hasResult()).isFalse();
        }
    }

    @Nested
    class 내_종료_작업_찾기 {

        @Test
        void 활성_작업은_제외하고_가장_최근에_끝난_작업을_고른다() {
            assertThat(repository.findLatestTerminalOf(room, member, now.minus(Duration.ofDays(3)))).isEmpty();

            repository.markJobCanceled(job.id(), now);
            assertThat(repository.findLatestTerminalOf(room, member, now.minus(Duration.ofDays(3)))).get()
                .extracting(OrganizeJob::publicId).isEqualTo(job.publicId());

            clock.advance(Duration.ofMinutes(2));
            OrganizeJob second = createJob(room, member);
            clock.advance(Duration.ofSeconds(61));
            repository.markJobFailed(second.id(), OrganizeFailureCode.LOST, null, clock.instant());
            assertThat(repository.findLatestTerminalOf(room, member, now.minus(Duration.ofDays(3)))).get()
                .extracting(OrganizeJob::publicId).isEqualTo(second.publicId());
        }

        @Test
        void 기준_시각_이전에_끝난_작업과_다른_회원의_작업은_제외한다() {
            repository.markJobCanceled(job.id(), now);

            assertThat(repository.findLatestTerminalOf(room, member, now.plusSeconds(1))).isEmpty();
            assertThat(repository.findLatestTerminalOf(room, nextId(), now.minusSeconds(60))).isEmpty();
        }
    }

    @Nested
    class 실행 {

        @Test
        void 전달만_실패해_닫힌_실행은_같은_실행으로_발송_대기에_다시_넣는다() {
            OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();
            assertThat(repository.rescheduleExecution(execution.id(), now, now.plusSeconds(60))).isFalse();
            repository.markExecutionFinished(execution.id(), OrganizeExecutionStatus.FAILED, now);

            assertThat(repository.rescheduleExecution(execution.id(), now, now.plusSeconds(60))).isTrue();

            OrganizeExecution again = repository.findLatestExecution(job.id()).orElseThrow();
            assertThat(again.status()).isEqualTo(OrganizeExecutionStatus.DISPATCH_PENDING);
            assertThat(again.executionId()).isEqualTo(execution.executionId());
            assertThat(again.finishedAt()).isNull();
        }

        @Test
        void 선점은_보낼_때가_된_실행을_한_번만_잡는다() {
            OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();

            assertThat(repository.claimExecution(execution.id(), now, now.plusSeconds(120))).isTrue();
            assertThat(repository.claimExecution(execution.id(), now, now.plusSeconds(120))).isFalse();
            assertThat(repository.claimExecution(execution.id(), now.plusSeconds(121), now.plusSeconds(240))).isTrue();
        }
    }

    @Nested
    class 익명_체험 {

        @Test
        void 회원과_방마다_한_번만_확보하고_작업으로_돌려준다() {
            Long anonymous = nextId();
            repository.markJobCanceled(job.id(), now);
            OrganizeJob first = repository.create(draft(room, anonymous), UUID.randomUUID(), List.of(), now,
                now.plusSeconds(60));
            repository.reserveTrial(anonymous, room, first.id(), now);
            assertThat(repository.hasTrial(anonymous, room)).isTrue();

            repository.markJobCanceled(first.id(), now);
            OrganizeJob second = repository.create(draft(room, anonymous), UUID.randomUUID(), List.of(), now,
                now.plusSeconds(60));
            assertThatThrownBy(() -> repository.reserveTrial(anonymous, room, second.id(), now))
                .isInstanceOf(DataIntegrityViolationException.class);
            Long otherRoom = nextId();
            OrganizeJob third = repository.create(draft(otherRoom, anonymous), UUID.randomUUID(), List.of(), now,
                now.plusSeconds(60));
            repository.reserveTrial(anonymous, otherRoom, third.id(), now);

            assertThat(repository.releaseTrial(first.id())).isTrue();
            assertThat(repository.hasTrial(anonymous, room)).isFalse();
            assertThat(repository.hasTrial(anonymous, otherRoom)).isTrue();
            assertThat(repository.releaseTrial(first.id())).isFalse();
            // 빈 대상 목록으로 만든 저장소 전용 작업은 종료해 다른 테스트의 발송 스위퍼가 잡지 않게 한다.
            repository.markJobCanceled(second.id(), now);
            repository.markJobCanceled(third.id(), now);
        }
    }

    @Nested
    class 접수_간격 {

        @Test
        void 첫_접수는_통과하고_간격_안의_다음은_막고_지나면_다시_통과한다() {
            Long other = nextId();
            Duration interval = Duration.ofSeconds(60);

            assertThat(repository.tryPassStartGate(other, now, interval)).isTrue();
            assertThat(repository.lastStartedAt(other)).contains(now);
            assertThat(repository.tryPassStartGate(other, now.plusSeconds(59), interval)).isFalse();
            assertThat(repository.tryPassStartGate(other, now.plusSeconds(60), interval)).isTrue();
            assertThat(repository.lastStartedAt(other)).contains(now.plusSeconds(60));
        }

        @Test
        void 회원마다_따로_센다() {
            Long first = nextId();
            Long second = nextId();
            repository.tryPassStartGate(first, now, Duration.ofSeconds(60));

            assertThat(repository.tryPassStartGate(second, now, Duration.ofSeconds(60))).isTrue();
        }
    }

    @Nested
    class 멱등_기록 {

        @Test
        void 저장한_기록을_키로_찾고_같은_키는_다시_저장할_수_없다() {
            repository.saveIdempotency(member, room, "START", "k".repeat(16), "a".repeat(64), job.id(), "{\"a\":1}", now);

            assertThat(repository.findIdempotency(member, room, "START", "k".repeat(16))).get()
                .satisfies(record -> {
                    assertThat(record.requestHash()).isEqualTo("a".repeat(64));
                    assertThat(record.jobId()).isEqualTo(job.id());
                    assertThat(record.response()).isEqualTo("{\"a\":1}");
                });
            assertThat(repository.findIdempotency(member, room, "RETRY", "k".repeat(16))).isEmpty();
            assertThatThrownBy(() -> repository.saveIdempotency(member, room, "START", "k".repeat(16), "b".repeat(64), null, null,
                now)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 기준_시각_이전에_만든_기록만_지운다() {
            repository.saveIdempotency(member, room, "START", "old-key-0123456789", "a".repeat(64), null, null, now);

            assertThat(repository.purgeIdempotencyBefore(now.minusSeconds(60))).isZero();
            assertThat(repository.purgeIdempotencyBefore(Instant.now().plusSeconds(3600))).isPositive();
            assertThat(repository.findIdempotency(member, room, "START", "old-key-0123456789")).isEmpty();
        }
    }

    @Nested
    class 방_점유_버전 {

        @Test
        void 처음에는_행을_만들고_바뀔_때마다_버전을_올린다() {
            Long freshRoom = nextId();
            assertThat(repository.findRoomState(freshRoom)).isEmpty();

            assertThat(repository.bumpRoomState(freshRoom, true, now))
                .isEqualTo(new OrganizeJobRepository.RoomState(1, true));
            assertThat(repository.bumpRoomState(freshRoom, false, now))
                .isEqualTo(new OrganizeJobRepository.RoomState(2, false));
            assertThat(repository.findRoomState(freshRoom)).get()
                .isEqualTo(new OrganizeJobRepository.RoomState(2, false));
        }
    }
}
