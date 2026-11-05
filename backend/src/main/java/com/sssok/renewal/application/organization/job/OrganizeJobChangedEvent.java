package com.sssok.renewal.application.organization.job;

// 작업 상태나 진행이 바뀌고 트랜잭션이 커밋되면 실시간 알림을 보내라는 신호.
// roomVersion 이 null 이 아니면 방 점유 상태도 함께 바뀐 것이다.
public record OrganizeJobChangedEvent(Long jobPk, Long roomId, Long roomVersion, boolean roomBusy) {
}
