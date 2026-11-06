package com.sssok.renewal.application.organization.job;

import java.util.UUID;

// Python 은 accepted=true 와 같은 executionId·state 를 받아야 재전송을 멈춘다.
public record OrganizeEventAck(boolean accepted, UUID executionId, String state) {
}
