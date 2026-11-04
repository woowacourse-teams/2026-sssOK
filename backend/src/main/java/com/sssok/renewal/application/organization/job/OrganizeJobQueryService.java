package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

// 조회는 요청자 본인만 할 수 있다. 남의 작업, 없는 작업, 다른 방의 작업은 모두 같은 404 로 답한다.
// 검토 기한이 지난 READY 는 읽을 때도 EXPIRED 로 바꿔서 돌려준다.
@Service
@RequiredArgsConstructor
public class OrganizeJobQueryService {
    private final OrganizeJobProperties properties;
    private final OrganizeJobRepository repository;
    private final OrganizeJobExpiry expiry;
    private final OrganizeMemberPolicy memberPolicy;
    @Qualifier("organizeClock")
    private final Clock clock;

    // 방이 사용 중인지는 누구의 작업이든 알려 주지만, 작업 상세는 요청자 본인의 것만 담는다.
    public record Current(boolean roomBusy, long roomVersion, OrganizeJob myJob, Boolean anonymousTrialAvailable) {
    }

    // 화면 재진입 스냅샷. 내 활성 작업을 우선하고 없으면 최근 보관 기간 안에 끝난 내 작업 하나를 돌려준다.
    public Current current(Long roomId, Long memberId) {
        expiry.expireActiveOfRoom(roomId);
        Optional<OrganizeJob> active = repository.findActiveByRoom(roomId);
        long version = repository.findRoomState(roomId).map(OrganizeJobRepository.RoomState::version).orElse(0L);
        OrganizeJob mine = active.filter(job -> job.isOwnedBy(memberId))
            .or(() -> repository.findLatestTerminalOf(roomId, memberId, clock.instant().minus(properties.retention())))
            .orElse(null);
        Boolean anonymousTrialAvailable = memberPolicy.isAnonymous(memberId)
            ? !repository.hasTrial(memberId, roomId) : null;
        return new Current(active.isPresent(), version, mine, anonymousTrialAvailable);
    }

    public OrganizeJob get(Long roomId, Long memberId, UUID jobId) {
        return expiry.refresh(peek(roomId, memberId, jobId));
    }

    // 기한을 검사하지 않고 읽기만 한다. 읽기 전용 트랜잭션 안에서는 이쪽을 쓴다.
    public OrganizeJob peek(Long roomId, Long memberId, UUID jobId) {
        return repository.findByPublicId(jobId)
            .filter(job -> job.roomId().equals(roomId) && job.isOwnedBy(memberId))
            .orElseThrow(() -> new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_FOUND));
    }
}
