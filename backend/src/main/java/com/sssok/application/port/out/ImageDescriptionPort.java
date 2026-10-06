package com.sssok.application.port.out;

import java.time.Duration;

public interface ImageDescriptionPort {
    // 구현체는 timeout 이내에 응답하거나 실패해야 한다. URL은 로그에 남기지 않는다.
    Description describe(String imageUrl, Duration timeout);

    record Description(String description, String features, String model, String promptVersion) {}
}
