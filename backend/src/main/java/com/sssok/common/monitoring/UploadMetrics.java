package com.sssok.common.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// HTTP 2xx 응답 안에도 파일별 거절이 포함된다. 응답 항목 수를 세며 고유 파일 수나 R2 PUT 성공률은 아니다.
@Component
public class UploadMetrics {

    private final MeterRegistry registry;

    public UploadMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (String stage : new String[]{"issue", "register"}) {
            for (String result : new String[]{"accepted", "rejected"}) {
                registry.counter("sssok.upload.files", "stage", stage, "result", result);
            }
        }
    }

    public void issued(int accepted, int rejected) {
        record("issue", accepted, rejected);
    }

    public void registered(int accepted, int rejected) {
        record("register", accepted, rejected);
    }

    private void record(String stage, int accepted, int rejected) {
        registry.counter("sssok.upload.files", "stage", stage, "result", "accepted").increment(accepted);
        registry.counter("sssok.upload.files", "stage", stage, "result", "rejected").increment(rejected);
    }
}
