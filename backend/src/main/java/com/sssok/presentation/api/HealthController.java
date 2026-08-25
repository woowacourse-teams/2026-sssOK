package com.sssok.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 배포 파이프라인과 컨테이너 healthcheck가 폴링하는 상태 확인 엔드포인트.
@Tag(name = "헬스체크", description = "배포 파이프라인·컨테이너 상태 확인용 엔드포인트")
@RestController
public class HealthController {

    @Operation(
        summary = "서버 상태 확인",
        description = "서버가 정상적으로 떠 있는지 확인한다. 인증이 필요 없고, "
            + "배포 파이프라인과 컨테이너 헬스체크가 주기적으로 호출한다. "
            + "다른 API와 달리 ApiResponse로 감싸지 않은 { \"status\": \"UP\" } 형태를 그대로 반환한다."
    )
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
