package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 화면 재진입 스냅샷과 요청자 전용 조회 규칙을 확인한다.
@OrganizeH2Test
class OrganizeJobQueryServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeJobQueryService queryService;
    @Autowired
    OrganizeCancelService cancelService;

    private Long host;
    private Long guest;
    private Long room;

    @BeforeEach
    void setUp() {
        host = registeredMember();
        guest = registeredMember();
        room = room(host);
        jpegs(room, host, 2);
    }

    @Test
    void 작업이_없으면_방은_비어_있고_버전은_0이다() {
        OrganizeJobQueryService.Current current = queryService.current(room, host);

        assertThat(current.roomBusy()).isFalse();
        assertThat(current.roomVersion()).isZero();
        assertThat(current.myJob()).isNull();
        assertThat(current.anonymousTrialAvailable()).isNull();
    }

    @Test
    void 익명_체험은_회원과_방마다_독립적으로_확인하고_조회로_소진하지_않는다() {
        Long anonymous = anonymousMember();
        Long other = anonymousMember();
        assertThat(queryService.current(room, anonymous).anonymousTrialAvailable()).isTrue();
        assertThat(queryService.current(room, anonymous).anonymousTrialAvailable()).isTrue();
        assertThat(repository.hasTrial(anonymous, room)).isFalse();

        startPerson(room, anonymous);

        assertThat(queryService.current(room, anonymous).anonymousTrialAvailable()).isFalse();
        assertThat(queryService.current(room, other).anonymousTrialAvailable()).isTrue();
        assertThat(queryService.current(room(anonymous), anonymous).anonymousTrialAvailable()).isTrue();
    }

    @Test
    void 발송_전_취소로_체험을_돌려받으면_다시_사용_가능하다() {
        Long anonymous = anonymousMember();
        OrganizeJob job = startPerson(room, anonymous);
        cancelService.cancel(room, anonymous, job.publicId());

        assertThat(queryService.current(room, anonymous).anonymousTrialAvailable()).isTrue();
    }

    @Test
    void 분석_실패로_체험을_돌려받으면_다시_사용_가능하다() {
        Long anonymous = anonymousMember();
        OrganizeJob job = startPerson(room, anonymous);
        states.analysisFailed(job);

        assertThat(queryService.current(room, anonymous).anonymousTrialAvailable()).isTrue();
    }

    @Test
    void 체험을_쓴_작업이_조회_기간을_지나도_사용권은_소진된_상태다() {
        Long anonymous = anonymousMember();
        OrganizeJob job = startPerson(room, anonymous);
        states.cancelAfterAccepted(job, anonymous);
        clock.advance(Duration.ofDays(4));

        OrganizeJobQueryService.Current current = queryService.current(room, anonymous);
        assertThat(current.myJob()).isNull();
        assertThat(current.anonymousTrialAvailable()).isFalse();
    }

    @Test
    void 체험을_쓴_뒤_가입한_회원은_익명_제한_대상이_아니다() {
        Long anonymous = anonymousMember();
        startPerson(room, anonymous);
        jdbc.update("UPDATE member SET status = 'REGISTERED' WHERE id = ?", anonymous);

        assertThat(queryService.current(room, anonymous).anonymousTrialAvailable()).isNull();
    }

    @Test
    void 내_활성_작업은_보이고_다른_멤버에게는_방이_사용_중이라는_사실만_보인다() {
        OrganizeJob job = startPerson(room, host);

        OrganizeJobQueryService.Current mine = queryService.current(room, host);
        OrganizeJobQueryService.Current others = queryService.current(room, guest);

        assertThat(mine.roomBusy()).isTrue();
        assertThat(mine.roomVersion()).isEqualTo(1);
        assertThat(mine.myJob().publicId()).isEqualTo(job.publicId());
        assertThat(others.roomBusy()).isTrue();
        assertThat(others.roomVersion()).isEqualTo(1);
        assertThat(others.myJob()).isNull();
    }

    @Test
    void 활성_작업이_없으면_3일_안에_끝난_내_작업_하나를_돌려준다() {
        OrganizeJob job = startPerson(room, host);
        states.accept(job);
        cancelService.cancel(room, host, job.publicId());

        OrganizeJobQueryService.Current current = queryService.current(room, host);

        assertThat(current.roomBusy()).isFalse();
        assertThat(current.roomVersion()).isEqualTo(2);
        assertThat(current.myJob().status()).isEqualTo(OrganizeJobStatus.CANCELED);
        assertThat(queryService.current(room, guest).myJob()).isNull();
    }

    @Test
    void 끝난_지_3일이_지난_작업은_복원하지_않는다() {
        OrganizeJob job = startPerson(room, host);
        states.accept(job);
        cancelService.cancel(room, host, job.publicId());
        clock.advance(Duration.ofDays(3).plusMinutes(1));

        assertThat(queryService.current(room, host).myJob()).isNull();
    }

    @Test
    void 검토_기한이_지난_READY는_읽는_순간_만료되고_방이_풀린다() {
        OrganizeJob job = startPerson(room, host);
        states.ready(job);
        clock.advance(Duration.ofMinutes(31));

        OrganizeJobQueryService.Current current = queryService.current(room, guest);

        assertThat(current.roomBusy()).isFalse();
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.EXPIRED);
        assertThat(queryService.current(room, host).myJob().status()).isEqualTo(OrganizeJobStatus.EXPIRED);
    }

    @Test
    void 단건_조회도_기한이_지난_READY를_EXPIRED로_돌려준다() {
        OrganizeJob job = startPerson(room, host);
        states.ready(job);
        clock.advance(Duration.ofMinutes(31));

        assertThat(queryService.get(room, host, job.publicId()).status()).isEqualTo(OrganizeJobStatus.EXPIRED);
    }

    @Test
    void 남의_작업이나_없는_작업이나_다른_방의_작업은_같은_404다() {
        OrganizeJob job = startPerson(room, host);

        for (Runnable attempt : new Runnable[] {
            () -> queryService.get(room, guest, job.publicId()),
            () -> queryService.get(room, host, UUID.randomUUID()),
            () -> queryService.get(room + 999, host, job.publicId())}) {
            assertThatThrownBy(attempt::run).isInstanceOfSatisfying(SssOkException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_FOUND));
        }
    }
}
