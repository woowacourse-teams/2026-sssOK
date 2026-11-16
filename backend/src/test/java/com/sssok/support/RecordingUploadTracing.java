package com.sssok.support;

import com.sssok.renewal.outgoing.monitoring.UploadTracing;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 실제 OTel 문맥과 동기 exporter로 스레드 전파·샘플링·span 종료를 검증한다. */
public class RecordingUploadTracing implements AutoCloseable {
    public final List<SpanData> spans = new CopyOnWriteArrayList<>();
    public final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(new SpanExporter() {
            public CompletableResultCode export(Collection<SpanData> batch) {
                spans.addAll(batch);
                return CompletableResultCode.ofSuccess();
            }
            public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        })).build();
    private final io.opentelemetry.api.trace.Tracer sdkTracer = provider.get("upload-test");
    public final OtelTracer tracer = new OtelTracer(sdkTracer, new OtelCurrentTraceContext(), event -> {});
    public final OtelPropagator propagator = new OtelPropagator(
        ContextPropagators.create(W3CTraceContextPropagator.getInstance()), sdkTracer);
    public final UploadTracing tracing = new UploadTracing(tracer, propagator, registry);

    @Override
    public void close() {
        provider.close();
        registry.close();
    }
}
