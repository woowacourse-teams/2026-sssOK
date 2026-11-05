package com.sssok.presentation.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// 사진 정리가 이미 진행 중일 때의 오류 본문. activeJobId 는 요청자 본인의 작업일 때만 채운다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizeInProgressErrorResponse(String code, String message, UUID activeJobId) {
}
