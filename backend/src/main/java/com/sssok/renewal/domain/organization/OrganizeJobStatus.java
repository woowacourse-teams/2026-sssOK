package com.sssok.renewal.domain.organization;

import java.util.List;

// 상태는 앞으로만 간다. 예외는 명시적 재시도(FAILED → QUEUED) 하나다. 이 규칙은 조건부 UPDATE 가 지킨다.
// 방을 점유하는 상태(QUEUED·RUNNING·READY)가 "활성 작업"이고, 분석이 진행 중인 상태(QUEUED·RUNNING)는 따로 구분한다.
// READY 는 검토 중이라 방을 계속 점유하며 APPLIED(반영)·CANCELED(검토 포기)·EXPIRED(검토 만료)로만 끝난다.
public enum OrganizeJobStatus {
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELED,
    APPLIED,
    EXPIRED;

    private static final List<OrganizeJobStatus> TERMINAL = List.of(FAILED, CANCELED, APPLIED, EXPIRED);

    public static List<OrganizeJobStatus> terminal() {
        return TERMINAL;
    }

    // 분석 서버의 이벤트와 발송을 처리할 수 있는 상태.
    public boolean isAnalyzing() {
        return this == QUEUED || this == RUNNING;
    }

    // 방의 활성 작업 제한을 차지하는 상태.
    public boolean holdsRoom() {
        return isAnalyzing() || this == READY;
    }

    // 진행 중이거나 검토 중인 작업만 취소할 수 있다.
    public boolean isCancelable() {
        return holdsRoom();
    }
}
