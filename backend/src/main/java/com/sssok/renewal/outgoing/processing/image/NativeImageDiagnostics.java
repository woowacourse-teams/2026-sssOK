package com.sssok.renewal.outgoing.processing.image;

import com.sssok.renewal.outgoing.monitoring.UploadTracing;
import com.sssok.renewal.outgoing.monitoring.UploadTracing.Stage;
import java.util.function.Function;

/** JNI의 평탄한 시간 배열을 해석한다. 이미지 처리기는 배열 구조와 span 매핑을 알 필요가 없다. */
final class NativeImageDiagnostics {
    // sssok_vips.c의 TimingField/DiagnosticStage와 동일한 JNI 전송 규약.
    private static final int START_MICROS = 0;
    private static final int DURATION_MICROS = 1;
    private static final int SUCCESS = 2;
    private static final int FIELDS_PER_STAGE = 3;
    private static final DiagnosticStage[] STAGES = DiagnosticStage.values();

    private final UploadTracing tracing;
    private final boolean enabled;

    NativeImageDiagnostics(UploadTracing tracing, boolean enabled) {
        this.tracing = tracing;
        this.enabled = enabled;
    }

    <T> T measure(Function<long[], T> operation) {
        if (tracing != null) {
            tracing.nativeExecution(enabled);
        }
        // null은 기존 fused 경로를 선택한다. 진단 모드만 시간 배열을 할당한다.
        long[] timings = enabled ? new long[STAGES.length * FIELDS_PER_STAGE] : null;
        try {
            return operation.apply(timings);
        } finally {
            if (timings != null && tracing != null) {
                record(timings);
            }
        }
    }

    private void record(long[] timings) {
        for (DiagnosticStage stage : STAGES) {
            int offset = stage.slot * FIELDS_PER_STAGE;
            long startMicros = timings[offset + START_MICROS];
            if (startMicros != 0) {
                tracing.nativeStage(stage.spanStage, stage.variant, startMicros,
                    timings[offset + DURATION_MICROS], timings[offset + SUCCESS] == 1);
            }
        }
    }

    private enum DiagnosticStage {
        HEADER(0, Stage.ORIENTATION, "source"),
        DECODE(1, Stage.DECODE, "source"),
        RESIZE_SHARED(2, Stage.RESIZE, "shared"),
        RESIZE_THUMBNAIL(3, Stage.RESIZE, "thumbnail"),
        ENCODE_THUMBNAIL(4, Stage.ENCODE, "thumbnail"),
        RESIZE_PREVIEW(5, Stage.RESIZE, "preview"),
        ENCODE_PREVIEW(6, Stage.ENCODE, "preview");

        private final int slot;
        private final Stage spanStage;
        private final String variant;

        DiagnosticStage(int slot, Stage spanStage, String variant) {
            this.slot = slot;
            this.spanStage = spanStage;
            this.variant = variant;
        }
    }
}
