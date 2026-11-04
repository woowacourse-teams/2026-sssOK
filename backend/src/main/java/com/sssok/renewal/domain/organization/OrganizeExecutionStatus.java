package com.sssok.renewal.domain.organization;

// 사용자에게는 보이지 않는 Python 분석 1회의 상태.
// DISPATCH_UNKNOWN 은 MCP 가 받았는지 모르는 상태라 같은 실행 ID 로만 다시 보낸다.
public enum OrganizeExecutionStatus {
    DISPATCH_PENDING,
    DISPATCHING,
    DISPATCH_UNKNOWN,
    ACCEPTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}
