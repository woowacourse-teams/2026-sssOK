package com.sssok.renewal.outgoing.storage;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class R2UploadStrategyResolver {
    static final int MULTIPART_THRESHOLD = 8 * 1024 * 1024;

    private final List<R2UploadStrategy> strategies;

    R2UploadStrategy resolve(int contentLength) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(contentLength))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 업로드 크기: " + contentLength));
    }
}
