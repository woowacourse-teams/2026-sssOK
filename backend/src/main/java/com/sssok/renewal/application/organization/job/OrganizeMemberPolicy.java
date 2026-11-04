package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 회원 유형별 사용 제한. 익명 회원은 (회원, 방)마다 체험 1회, OAuth 회원은 횟수 제한 없이 접수 간격만 둔다.
// 사용권은 작업을 저장하는 트랜잭션 안에서 확보해야 동시 접수에서도 하나만 통과한다.
@Component
@RequiredArgsConstructor
public class OrganizeMemberPolicy {
    private final MemberRepository members;
    private final OrganizeJobRepository repository;
    private final OrganizeJobProperties properties;

    public boolean isAnonymous(Long memberId) {
        return members.findById(memberId)
            .map(member -> member.getStatus() == MemberStatus.ANONYMOUS)
            .orElse(false);
    }

    // 모델이나 MCP 를 부르기 전에 읽기만으로 걸러 내는 사전 검사. 최종 판정은 저장할 때 다시 한다.
    public void precheck(Long memberId, Long roomId, Instant now) {
        if (isAnonymous(memberId)) {
            if (repository.hasTrial(memberId, roomId)) {
                throw new OrganizeJobException(ErrorCode.ORGANIZE_TRIAL_EXHAUSTED);
            }
            return;
        }
        repository.lastStartedAt(memberId).ifPresent(last -> {
            if (last.plus(properties.startInterval()).isAfter(now)) {
                throw rateLimited(last, now);
            }
        });
    }

    // 작업 저장과 같은 트랜잭션에서 부른다. 실패하면 저장 전체가 되돌아가 간격이 시작되지도 않는다.
    public void acquire(Long memberId, Long roomId, Long jobId, Instant now) {
        if (isAnonymous(memberId)) {
            reserveTrial(memberId, roomId, jobId, now);
            return;
        }
        if (!repository.tryPassStartGate(memberId, now, properties.startInterval())) {
            Instant last = repository.lastStartedAt(memberId).orElse(now);
            throw rateLimited(last, now);
        }
    }

    // 재시도에서는 간격을 새로 시작하지 않고 익명 사용권만 다시 확보한다.
    public void reacquireTrial(Long memberId, Long roomId, Long jobId, Instant now) {
        if (isAnonymous(memberId)) {
            reserveTrial(memberId, roomId, jobId, now);
        }
    }

    private void reserveTrial(Long memberId, Long roomId, Long jobId, Instant now) {
        if (repository.hasTrial(memberId, roomId)) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_TRIAL_EXHAUSTED);
        }
        repository.reserveTrial(memberId, roomId, jobId, now);
    }

    private OrganizeRateLimitedException rateLimited(Instant last, Instant now) {
        Duration remaining = Duration.between(now, last.plus(properties.startInterval()));
        return new OrganizeRateLimitedException(Math.max(1, remaining.toSeconds() + 1));
    }
}
