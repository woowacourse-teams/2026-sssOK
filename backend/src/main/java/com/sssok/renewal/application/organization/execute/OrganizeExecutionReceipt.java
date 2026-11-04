package com.sssok.renewal.application.organization.execute;

import java.util.Objects;
import java.util.UUID;

public record OrganizeExecutionReceipt(UUID executionId, State state, boolean reused) {
    public enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public OrganizeExecutionReceipt {
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(state);
    }
}
