package com.sssok.presentation.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 배포 파이프라인과 컨테이너 healthcheck가 폴링하는 상태 확인 엔드포인트.
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}