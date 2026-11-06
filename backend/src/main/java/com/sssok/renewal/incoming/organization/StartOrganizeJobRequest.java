package com.sssok.renewal.incoming.organization;

import com.sssok.renewal.domain.organization.OrganizeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "버튼으로 정리 시작")
public record StartOrganizeJobRequest(
    @Schema(description = "PERSON(인물별) 또는 SIMILAR(비슷한 사진끼리)")
    @NotNull(message = "정리 종류를 선택해주세요") OrganizeKind kind
) {
}
