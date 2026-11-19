package com.sssok.renewal.outgoing.monitoring;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Result;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Source;
import com.sssok.renewal.outgoing.monitoring.UploadTracing.Stage;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 업로드 계측 경계를 한곳에서 정의한다. 대상은 외부에서 호출하는 Spring bean 메서드다.
 * 트랜잭션 advice보다 먼저 시작해 complete의 커밋과 동기 AFTER_COMMIT 리스너까지 포함한다.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class UploadTracingAspect {
    private final UploadTracing tracing;
    private final MediaProcessingMetrics processingMetrics;

    @Around("execution(* com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics.measure(..)) || "
        + "execution(* com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics.total(..))")
    public Object registration(ProceedingJoinPoint call) throws Throwable {
        var stage = call.getSignature().getName().equals("total")
            ? UploadRegistrationMetrics.Stage.TOTAL : (UploadRegistrationMetrics.Stage) call.getArgs()[0];
        return tracing.registration(stage, call::proceed);
    }

    @Around("execution(* com.sssok.renewal.outgoing.database.ThumbnailJobRepository.enqueue(..)) || "
        + "execution(* com.sssok.renewal.outgoing.database.ThumbnailJobRepository.enqueueAll(..))")
    public Object enqueue(ProceedingJoinPoint call) throws Throwable {
        var context = tracing.capture();
        if (call.getArgs()[0] instanceof ThumbnailJob job) {
            job.traced(context);
        } else {
            for (Object job : (List<?>) call.getArgs()[0]) {
                ((ThumbnailJob) job).traced(context);
            }
        }
        return call.proceed();
    }

    @Around("bean(uploadHeadTaskExecutor) && execution(* submit(java.util.concurrent.Callable))")
    public Object submitHead(ProceedingJoinPoint call) throws Throwable {
        var context = tracing.capture();
        Callable<?> action = (Callable<?>) call.getArgs()[0];
        // Runnable/FutureTask 자체를 감싸지 않아 취소한 Future의 큐 purge 동작을 유지한다.
        Callable<?> continued = () -> tracing.head(context, action::call);
        return call.proceed(new Object[]{continued});
    }

    @Around("execution(* com.sssok.renewal.application.media.thumbnail.GenerateThumbnailService.generate(..))")
    public Object processing(ProceedingJoinPoint call) throws Throwable {
        ThumbnailClaim claim = (ThumbnailClaim) call.getArgs()[0];
        Source source = (Source) call.getArgs()[1];
        Timer.Sample timer = processingMetrics.start();
        Result result = Result.RETRYABLE;
        try {
            result = tracing.attempt(claim, () -> (Result) call.proceed());
            return result;
        } finally {
            processingMetrics.completed(timer, claim.kind(), source, result);
        }
    }

    @Around("execution(* com.sssok.renewal.outgoing.storage.ThumbnailMediaStorage.download(..)) || "
        + "execution(* com.sssok.renewal.outgoing.storage.ThumbnailMediaStorage.uploadThumbnail(..)) || "
        + "execution(* com.sssok.renewal.outgoing.storage.ThumbnailMediaStorage.uploadPreview(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.derive(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.readCaptureInfo(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.image.ImageDerivativeOperations.readOrientation(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.image.ImageDerivativeOperations.decode(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.image.ImageDerivativeOperations.resizeAndOrient(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.image.ImageDerivativeOperations.encodeDerivative(..)) || "
        + "execution(* com.sssok.renewal.outgoing.processing.video.FfmpegVideoFrameExtractor.extractFirstFrame(..)) || "
        + "execution(* com.sssok.renewal.application.media.thumbnail.ThumbnailJobCompletionService.complete(..)) || "
        + "execution(* com.sssok.application.media.ReadyMediaReader.read(..))")
    public Object stage(ProceedingJoinPoint call) throws Throwable {
        Stage stage = switch (call.getSignature().getName()) {
            case "download" -> Stage.DOWNLOAD;
            case "uploadThumbnail" -> Stage.UPLOAD_THUMBNAIL;
            case "uploadPreview" -> Stage.UPLOAD_PREVIEW;
            case "derive" -> Stage.DERIVE;
            case "readCaptureInfo" -> Stage.METADATA;
            case "readOrientation" -> Stage.ORIENTATION;
            case "decode" -> Stage.DECODE;
            case "resizeAndOrient" -> Stage.RESIZE;
            case "encodeDerivative" -> Stage.ENCODE;
            case "extractFirstFrame" -> Stage.VIDEO_EXTRACT;
            case "complete" -> Stage.COMPLETE;
            case "read" -> Stage.READY_READ;
            default -> throw new IllegalStateException("등록하지 않은 업로드 계측 경계");
        };
        return tracing.stage(stage, call::proceed);
    }

    @Around("execution(* com.sssok.application.port.out.EventPublisherPort.publish(java.lang.Long, java.lang.String, java.lang.Object))")
    public Object publishReady(ProceedingJoinPoint call) throws Throwable {
        if ("media.ready".equals(call.getArgs()[1])) {
            return tracing.stage(Stage.READY_PUBLISH, call::proceed);
        }
        return call.proceed();
    }
}
