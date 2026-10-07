package com.sssok.application.port.out;

import java.time.Duration;

// 구현체 내부의 자동 재시도는 금지한다. 작업 재시도는 DB 워커에서만 수행한다.
// 호출 실패는 AiCallException으로 미처리 확정/영구 실패/결과 불명을 구분한다.
public interface ImageDescriptionPort {
    // 구현체는 timeout 이내에 응답하거나 실패해야 한다. URL은 로그에 남기지 않는다.
    Description describe(String imageUrl, Duration timeout);

    record Description(String description, String features, String model, String promptVersion) {
    }
}
