package com.sssok.renewal.application.organization.job;

// 작업 저장이 커밋된 뒤 바로 발송을 시도하게 하는 신호. 놓쳐도 스위퍼가 다시 집어 간다.
public record OrganizeJobCreatedEvent(Long executionPk) {
}
