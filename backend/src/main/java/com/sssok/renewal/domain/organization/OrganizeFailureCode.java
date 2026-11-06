package com.sssok.renewal.domain.organization;

// 사용자 API 의 failureCode 로 그대로 나간다. 이름을 바꾸면 API 계약이 바뀐다.
public enum OrganizeFailureCode {
    AUTHENTICATION_FAILED,
    TOOL_CHANGED,
    REJECTED,
    DISPATCH_TIMEOUT,
    TARGET_CHANGED,
    NOT_STARTED,
    LOST,
    TIMEOUT,
    RESULT_INVALID,
    ANALYSIS_FAILED
}
