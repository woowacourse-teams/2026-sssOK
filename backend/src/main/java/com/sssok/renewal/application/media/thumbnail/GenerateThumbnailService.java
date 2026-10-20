package com.sssok.renewal.application.media.thumbnail;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.outgoing.storage.AbortableOutputStream;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.CaptureInfo;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImage;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImages;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor;
import com.sssok.renewal.outgoing.processing.video.FfmpegVideoFrameExtractor.ExtractedFrame;
import com.sssok.renewal.outgoing.processing.video.FfmpegVideoFrameExtractor.ExtractionResult;
import com.sssok.renewal.outgoing.processing.video.FfmpegVideoFrameExtractor;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Result;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Source;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.DerivativeFormat;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.DerivativeImageProperties.Variant;
import com.sssok.infrastructure.config.DerivativeImageProperties;
import com.sssok.renewal.outgoing.config.VideoProperties;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 변환·스토리지 입출력 동안 DB 트랜잭션을 유지하지 않는다.
// 상태 변경은 ThumbnailJobCompletionService의 짧은 트랜잭션에서 처리한다.
@Slf4j
@Service("renewalGenerateThumbnailService")
@RequiredArgsConstructor
public class GenerateThumbnailService {

    private final UploadFileRepository uploadFileRepository;
    private final FileStorage fileStorage;
    private final ThumbnailatorImageProcessor imageProcessor;
    private final FfmpegVideoFrameExtractor videoFrameExtractor;
    private final ThumbnailJobService thumbnailJobService;
    private final ThumbnailJobCompletionService thumbnailJobCompletionService;
    private final DerivativeImageProperties imageProperties;
    private final VideoProperties videoProperties;

    private final MediaProcessingMetrics metrics;

    public void generate(ThumbnailClaim claimedJob, Source source) {
        Timer.Sample processingTimer = metrics.start();
        Result processingResult = Result.RETRYABLE;
        try {
            processingResult = generateAttempt(claimedJob);
        } finally {
            metrics.completed(processingTimer, claimedJob.kind(), source, processingResult);
        }
    }

    private Result generateAttempt(ThumbnailClaim claimedJob) {
        if (thumbnailJobService.renewLease(claimedJob) != ThumbnailJobService.Heartbeat.RENEWED) {
            return Result.SKIPPED;
        }
        StoredFile storedFile = uploadFileRepository.findById(claimedJob.mediaId()).orElse(null);
        if (storedFile == null || storedFile.getStatus() != UploadStatus.PROCESSING) {
            thumbnailJobCompletionService.completeSkipped(claimedJob);
            return Result.SKIPPED;
        }
        try {
            return process(storedFile, claimedJob);
        } catch (RuntimeException failure) {
            // 일시 실패는 백오프 후 재시도한다. 최대 시도 횟수를 넘으면 최종 실패로 수렴한다.
            log.warn("썸네일 생성 실패 mediaId={} claimToken={}", claimedJob.mediaId(), claimedJob.token(), failure);
            thumbnailJobCompletionService.fail(claimedJob, false, "PROCESSING_ERROR");
            return Result.RETRYABLE;
        }
    }

    private Result process(StoredFile storedFile, ThumbnailClaim claimedJob) {
        if (storedFile.getMediaType().isVideo()) {
            return processVideo(storedFile, claimedJob);
        }
        return processImage(storedFile, claimedJob);
    }

    // 영상 원본을 메모리에 올리지 않고, 서명 URL을 추출기에 전달한다.
    private Result processVideo(StoredFile storedFile, ThumbnailClaim claimedJob) {
        String sourceUrl = fileStorage.presignGet(storedFile.getStorageKey(), "inline",
            storedFile.getMediaType().contentType(), videoProperties.sourceUrlTtl());

        ExtractionResult frameExtractionResult = videoFrameExtractor.extractFirstFrame(
            sourceUrl, videoProperties.thumbnailMaxWidth());
        if (frameExtractionResult.retryable()) {
            // 영상이 깨졌다는 근거가 없는데 여기서 확정하면 멀쩡한 영상이 썸네일 없이 굳는다.
            log.warn("영상 처리가 일시적으로 실패했습니다. 백오프 후 다시 시도합니다. mediaId={}",
                storedFile.getId());
            thumbnailJobCompletionService.fail(claimedJob, false, "VIDEO_RETRYABLE");
            return Result.RETRYABLE;
        }
        if (!frameExtractionResult.hasFrame()) {
            // 프레임 추출이 불가능해도 원본을 사용할 수 있도록 썸네일 없이 완료한다.
            log.warn("영상에서 프레임을 뽑지 못했습니다. 썸네일 없이 완료합니다. mediaId={}", storedFile.getId());
            return thumbnailJobCompletionService.complete(claimedJob, ProcessedMedia.none())
                ? Result.COMPLETED_WITHOUT_THUMBNAIL : Result.SKIPPED;
        }

        return uploadVideoThumbnailAndComplete(storedFile, claimedJob, frameExtractionResult.frame());
    }

    private Result uploadVideoThumbnailAndComplete(StoredFile storedFile, ThumbnailClaim claimedJob,
            ExtractedFrame extractedFrame) {
        // 추출 결과는 JPEG이므로 원본 영상 확장자 대신 JPEG 경로와 Content-Type을 사용한다.
        StorageKey thumbnailKey = storedFile.getStorageKey().attempt(claimedJob.token()).thumbnail(MediaType.JPEG.extension());
        // 워커가 중단돼도 복구 시 정리할 수 있도록 업로드 전에 경로를 기록한다.
        if (!thumbnailJobService.recordGeneratedFiles(claimedJob, thumbnailKey, null)) {
            return Result.SKIPPED;
        }
        try {
            upload(thumbnailKey, extractedFrame.content(), DerivativeContentType.of(thumbnailKey));

            boolean completionAccepted = thumbnailJobCompletionService.complete(claimedJob, ProcessedMedia.ofVideo(
                thumbnailKey, extractedFrame.width(), extractedFrame.height(), extractedFrame.durationSeconds(),
                extractedFrame.takenAt(), extractedFrame.location()));
            return completionAccepted ? Result.SUCCESS : Result.SKIPPED;
        } catch (RuntimeException | Error failure) {
            thumbnailJobCompletionService.enqueueUnusedFilesForCleanup(claimedJob.mediaId(), thumbnailKey);
            throw failure;
        }
    }

    private Result processImage(StoredFile storedFile, ThumbnailClaim claimedJob) {
        // 원본 다운로드 실패는 바깥에서 재시도 가능한 실패로 처리한다.
        byte[] originalImageBytes = downloadOriginalImage(storedFile.getStorageKey());

        DerivativeFormat derivativeFormat = imageProperties.format();
        // 원본을 한 번만 디코딩하도록 썸네일과 프리뷰를 함께 생성한다.
        Optional<DerivedImages> derivedImagesResult = imageProcessor.derive(originalImageBytes,
            toDerivativeSpec(imageProperties.thumbnail(), derivativeFormat),
            storedFile.needsPreview() ? toDerivativeSpec(imageProperties.preview(), derivativeFormat) : null);
        if (derivedImagesResult.isEmpty()) {
            // 해석 불가능한 이미지는 영구 실패다. 변환 중 예외는 바깥에서 재시도 처리한다.
            log.warn("이미지를 읽을 수 없습니다. mediaId={}", storedFile.getId());
            thumbnailJobCompletionService.fail(claimedJob, true, "INVALID_IMAGE");
            return Result.PERMANENT_FAILURE;
        }

        return uploadImageAndComplete(storedFile, claimedJob, derivedImagesResult.get(),
            originalImageBytes, derivativeFormat);
    }

    private Result uploadImageAndComplete(StoredFile storedFile, ThumbnailClaim claimedJob,
            DerivedImages derivedImages, byte[] originalImageBytes, DerivativeFormat derivativeFormat) {
        StorageKey thumbnailKey = storedFile.getStorageKey().attempt(claimedJob.token()).thumbnail(derivativeFormat.extension());
        StorageKey previewKey = derivedImages.hasPreview()
            ? storedFile.getStorageKey().attempt(claimedJob.token()).preview(derivativeFormat.extension()) : null;
        // 워커가 중단돼도 복구 시 정리할 수 있도록 업로드 전에 경로를 기록한다.
        if (!thumbnailJobService.recordGeneratedFiles(claimedJob, thumbnailKey, previewKey)) {
            return Result.SKIPPED;
        }
        try {
            upload(thumbnailKey, derivedImages.thumbnail());
            if (previewKey != null) {
                upload(previewKey, derivedImages.preview());
            }
            CaptureInfo captureInfo = imageProcessor.readCaptureInfo(originalImageBytes);
            boolean completionAccepted = thumbnailJobCompletionService.complete(claimedJob, ProcessedMedia.ofImage(thumbnailKey, previewKey,
                derivedImages.sourceWidth(), derivedImages.sourceHeight(), captureInfo.takenAt(), captureInfo.location()));
            return completionAccepted ? Result.SUCCESS : Result.SKIPPED;
        } catch (RuntimeException | Error failure) {
            thumbnailJobCompletionService.enqueueUnusedFilesForCleanup(claimedJob.mediaId(), thumbnailKey, previewKey);
            throw failure;
        }
    }

    private DerivativeSpec toDerivativeSpec(Variant variant, DerivativeFormat derivativeFormat) {
        return new DerivativeSpec(variant.maxWidth(), derivativeFormat, variant.quality());
    }

    private void upload(StorageKey storageKey, DerivedImage derivedImage) {
        upload(storageKey, derivedImage.content(), DerivativeContentType.of(storageKey));
    }

    // 크기가 제한된 이미지에만 사용한다. 영상 원본은 메모리에 올리지 않는다.
    private byte[] downloadOriginalImage(StorageKey storageKey) {
        try (InputStream downloadStream = fileStorage.openDownloadStream(storageKey)) {
            return downloadStream.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    // 업로드 실패 시 미완료 멀티파트 조각이 남지 않도록 중단한다.
    private void upload(StorageKey storageKey, byte[] content, String contentType) {
        AbortableOutputStream uploadStream = fileStorage.openUploadStream(storageKey, contentType);
        try {
            uploadStream.write(content);
            uploadStream.close();
        } catch (IOException failure) {
            uploadStream.abort();
            throw new UncheckedIOException(failure);
        } catch (RuntimeException failure) {
            uploadStream.abort();
            throw failure;
        }
    }
}
