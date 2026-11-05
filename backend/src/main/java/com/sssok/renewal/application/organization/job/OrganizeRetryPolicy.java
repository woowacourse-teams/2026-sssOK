package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 어떤 실패를 어떻게 다시 시도할 수 있는지 한곳에서 정한다.
// 원격 실행이 끝났는지 확인할 수 없는 실패(NOT_STARTED·LOST·TIMEOUT)는 새 분석을 만들면 중복 실행이 될 수 있어 막는다.
@Component
@RequiredArgsConstructor
public class OrganizeRetryPolicy {
    public enum Mode {
        // 전달만 실패했다. 같은 실행 ID 로 다시 보낸다. 새 분석을 만들지 않으므로 횟수에 넣지 않는다.
        RESEND,
        // 분석 서버가 실패로 끝났다고 알려 왔다. 같은 작업에 새 실행 회차를 만든다.
        REANALYZE
    }

    private final OrganizeJobProperties properties;

    public Optional<Mode> modeOf(OrganizeJob job) {
        if (job.status() != OrganizeJobStatus.FAILED || job.failureCode() == null) {
            return Optional.empty();
        }
        if (job.failureCode().equals(OrganizeFailureCode.DISPATCH_TIMEOUT.name())) {
            return Optional.of(Mode.RESEND);
        }
        if (job.failureCode().equals(OrganizeFailureCode.ANALYSIS_FAILED.name())
            && job.retryCount() < properties.maxUserRetries()) {
            return Optional.of(Mode.REANALYZE);
        }
        return Optional.empty();
    }

    public boolean retryable(OrganizeJob job) {
        return modeOf(job).isPresent();
    }

    public Instant retryAvailableAt(OrganizeJob job) {
        if (!retryable(job) || job.finishedAt() == null) {
            return null;
        }
        return job.finishedAt().plus(properties.userRetryDelay(job.retryCount()));
    }
}
