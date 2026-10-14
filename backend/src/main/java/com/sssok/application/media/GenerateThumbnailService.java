package com.sssok.application.media;

import com.sssok.common.monitoring.MediaProcessingMetrics;
import com.sssok.application.media.thumbnail.ThumbnailClaim;
import com.sssok.application.media.thumbnail.ThumbnailJobs;
import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import com.sssok.common.monitoring.MediaProcessingMetrics.Source;
import com.sssok.common.monitoring.MediaProcessingMetrics.Result;
import io.micrometer.core.instrument.Timer;
import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageProcessorPort;
import com.sssok.application.port.out.ImageProcessorPort.CaptureInfo;
import com.sssok.application.port.out.ImageProcessorPort.DerivativeSpec;
import com.sssok.application.port.out.ImageProcessorPort.DerivedImage;
import com.sssok.application.port.out.ImageProcessorPort.DerivedImages;
import com.sssok.application.port.out.VideoFrameExtractorPort;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractedFrame;
import com.sssok.application.port.out.VideoFrameExtractorPort.ExtractionResult;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.DerivativeFormat;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.DerivativeImageProperties;
import com.sssok.infrastructure.config.DerivativeImageProperties.Variant;
import com.sssok.infrastructure.config.VideoProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 등록된 미디어를 PROCESSING 에서 READY 로 넘기는 워커.
//
// 트랜잭션을 열지 않는다. 원본을 내려받고 줄여서 다시 올리는 동안 DB 커넥션을 쥐고 있으면
// 사진 몇 장만 처리해도 풀이 마른다. 실제 쓰기는 ThumbnailJobs와 MediaFinisher가 짧게 연다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateThumbnailService {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final ImageProcessorPort imageProcessor;
    private final VideoFrameExtractorPort videoFrameExtractor;
    private final ThumbnailJobs jobs;
    private final DerivativeImageProperties imageProperties;
    private final VideoProperties videoProperties;

    private final MediaProcessingMetrics metrics;

    public void generate(ThumbnailClaim claim, Source source) {
        Timer.Sample sample = metrics.start();
        Result result = Result.RETRYABLE;
        try {
            result = generateAttempt(claim);
        } finally {
            metrics.completed(sample, claim.kind(), source, result);
        }
    }

    private Result generateAttempt(ThumbnailClaim claim) {
        if (jobs.heartbeat(claim) != ThumbnailJobs.Heartbeat.RENEWED) { return Result.SKIPPED; }
        StoredFile file = fileRepository.findById(claim.mediaId()).orElse(null);
        if (file == null || file.getStatus() != UploadStatus.PROCESSING) {
            jobs.skip(claim);
            return Result.SKIPPED;
        }
        try {
            return process(file, claim);
        } catch (RuntimeException e) {
            // 일시 실패는 백오프 후 재시도한다. 최대 시도 횟수를 넘으면 최종 실패로 수렴한다.
            log.warn("썸네일 생성 실패 mediaId={} claimToken={}", claim.mediaId(), claim.token(), e);
            jobs.failed(claim, false, "PROCESSING_ERROR");
            return Result.RETRYABLE;
        }
    }

    private Result process(StoredFile file, ThumbnailClaim claim) {
        if (file.getMediaType().isVideo()) {
            return processVideo(file, claim);
        }
        return processImage(file, claim);
    }

    // 영상은 원본을 내려받지 않는다. 최대 1GB 라 통째로 메모리에 올리면 서버가 죽는다.
    // 대신 서명 URL 을 추출기에 넘겨, 프레임 한 장에 필요한 구간만 Range 로 읽게 한다.
    private Result processVideo(StoredFile file, ThumbnailClaim claim) {
        String sourceUrl = fileStoragePort.presignGet(file.getStorageKey(), "inline",
            file.getMediaType().contentType(), videoProperties.sourceUrlTtl());

        ExtractionResult extracted = videoFrameExtractor.extractFirstFrame(
            sourceUrl, videoProperties.thumbnailMaxWidth());
        if (extracted.retryable()) {
            // 영상이 깨졌다는 근거가 없는데 여기서 확정하면 멀쩡한 영상이 썸네일 없이 굳는다.
            log.warn("영상 처리가 일시적으로 실패했습니다. 백오프 후 다시 시도합니다. mediaId={}",
                file.getId());
            jobs.failed(claim, false, "VIDEO_RETRYABLE");
            return Result.RETRYABLE;
        }
        if (!extracted.hasFrame()) {
            // 깨졌거나 코덱을 읽지 못하는 영상이다. 다시 태워도 결과가 같다.
            // FAILED 로 내리면 원본이 멀쩡한데 목록에서 사라지고, PROCESSING 에 두면 회수 배치가
            // 영영 다시 집어 드므로, 썸네일 없이 완료로 넘긴다.
            log.warn("영상에서 프레임을 뽑지 못했습니다. 썸네일 없이 완료합니다. mediaId={}", file.getId());
            return jobs.finish(claim, ProcessedMedia.none())
                ? Result.COMPLETED_WITHOUT_THUMBNAIL : Result.SKIPPED;
        }

        ExtractedFrame frame = extracted.frame();
        // 추출기가 JPEG 프레임을 준다. 원본 확장자(mp4 등)를 그대로 쓰면 브라우저가 이미지로
        // 그리지 못하므로 키도 Content-Type 도 JPEG 으로 맞춘다.
        StorageKey thumbnailKey = file.getStorageKey().attempt(claim.token()).thumbnail(MediaType.JPEG.extension());
        if (!jobs.artifacts(claim, thumbnailKey, null)) { return Result.SKIPPED; }
        try {
            upload(thumbnailKey, frame.content(), DerivativeContentType.of(thumbnailKey));

            boolean accepted = jobs.finish(claim, ProcessedMedia.ofVideo(
                thumbnailKey, frame.width(), frame.height(), frame.durationSeconds(),
                frame.takenAt(), frame.location()));
            return accepted ? Result.SUCCESS : Result.SKIPPED;
        } catch (RuntimeException | Error failure) {
            jobs.cleanup(claim.mediaId(), thumbnailKey);
            throw failure;
        }
    }

    private Result processImage(StoredFile file, ThumbnailClaim claim) {
        // 원본이 없으면 여기서 예외가 난다. 등록 때 실물을 확인했으므로 원래는 있어야 하고,
        // 지금 안 보이는 것은 일시적일 수 있어 바깥에서 PROCESSING 으로 남긴다.
        byte[] original = readOriginal(file.getStorageKey());

        DerivativeFormat format = imageProperties.format();
        // 목록 타일용과 상세 모달용을 한 번에 만든다. 나눠 부르면 원본을 두 번 디코딩하는데,
        // 디코딩이 이 작업 시간의 대부분이다 (docs/backend/IMAGE_DERIVATIVE_FORMAT.md).
        Optional<DerivedImages> derived = imageProcessor.derive(original,
            specOf(imageProperties.thumbnail(), format),
            file.needsPreview() ? specOf(imageProperties.preview(), format) : null);
        if (derived.isEmpty()) {
            // 파일이 깨졌거나 확장자와 실제 내용이 다르다. 다시 시도해도 결과가 같으므로
            // 재시도 없이 FAILED 로 확정한다.
            // 축소·인코딩이 흔들린 것은 여기로 오지 않고 예외로 올라가 PROCESSING 에 남는다.
            log.warn("이미지를 읽을 수 없습니다. mediaId={}", file.getId());
            jobs.failed(claim, true, "INVALID_IMAGE");
            return Result.PERMANENT_FAILURE;
        }

        DerivedImages images = derived.get();
        StorageKey thumbnailKey = file.getStorageKey().attempt(claim.token()).thumbnail(format.extension());
        StorageKey previewKey = images.hasPreview()
            ? file.getStorageKey().attempt(claim.token()).preview(format.extension()) : null;
        if (!jobs.artifacts(claim, thumbnailKey, previewKey)) { return Result.SKIPPED; }
        try {
            upload(thumbnailKey, images.thumbnail());
            if (previewKey != null) { upload(previewKey, images.preview()); }
            CaptureInfo capture = imageProcessor.readCaptureInfo(original);
            boolean accepted = jobs.finish(claim, ProcessedMedia.ofImage(thumbnailKey, previewKey,
                images.sourceWidth(), images.sourceHeight(), capture.takenAt(), capture.location()));
            return accepted ? Result.SUCCESS : Result.SKIPPED;
        } catch (RuntimeException | Error failure) {
            jobs.cleanup(claim.mediaId(), thumbnailKey, previewKey);
            throw failure;
        }
    }

    private DerivativeSpec specOf(Variant variant, DerivativeFormat format) {
        return new DerivativeSpec(variant.maxWidth(), format, variant.quality());
    }

    private StorageKey upload(StorageKey key, DerivedImage image) {
        upload(key, image.content(), DerivativeContentType.of(key));
        return key;
    }

    // 이미지는 upload.image-max-size 로 묶여 있어 통째로 읽는다. 축소하려면 어차피 전체가 필요하다.
    // 상한을 올릴 때는 워커 동시 실행 수와 힙 사용량을 함께 봐야 한다.
    // 영상은 여기까지 오지 않는다 — 영상 상한을 힙에 올리면 서버가 죽는다.
    private byte[] readOriginal(StorageKey storageKey) {
        try (InputStream in = fileStoragePort.openDownloadStream(storageKey)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 실패하면 abort 로 정리한다. 그냥 두면 완료되지 않은 멀티파트 조각이 스토리지에 남아 요금을 먹는다.
    private void upload(StorageKey storageKey, byte[] content, String contentType) {
        AbortableOutputStream out = fileStoragePort.openUploadStream(storageKey, contentType);
        try {
            out.write(content);
            out.close();
        } catch (IOException e) {
            out.abort();
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            out.abort();
            throw e;
        }
    }
}
