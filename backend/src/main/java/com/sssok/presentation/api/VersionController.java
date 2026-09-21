package com.sssok.presentation.api;

import com.sssok.common.version.VersionInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 지금 떠 있는 게 어떤 빌드인지 확인하는 엔드포인트. 배포 직후 검증과 장애 대응에 쓴다.
// HealthController 와 같은 이유로 presentation.api 바로 아래 둔다 — /api/v1 접두사를 받지 않는다.
@Tag(name = "버전", description = "실행 중인 백엔드의 버전·배포 커밋 확인용 엔드포인트")
@RestController
@RequiredArgsConstructor
public class VersionController {

    private final VersionInfo versionInfo;

    @Operation(
        summary = "실행 중인 버전 확인",
        description = "통합 릴리스 버전·백엔드 버전·배포 커밋 SHA를 반환한다. 인증이 필요 없고, "
            + "헬스체크와 마찬가지로 ApiResponse로 감싸지 않은 평평한 형태를 그대로 반환한다. "
            + "배포 파이프라인이 값을 주입하지 않은 로컬 실행에서는 unknown이 내려온다."
    )
    @GetMapping("/version")
    public VersionInfo version() {
        return versionInfo;
    }
}
