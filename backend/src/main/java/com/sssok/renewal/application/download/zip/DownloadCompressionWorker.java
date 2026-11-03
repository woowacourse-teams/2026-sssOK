package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.outgoing.monitoring.DownloadProcessingMetrics;
import io.micrometer.core.instrument.Timer;
import com.sssok.renewal.outgoing.storage.AbortableOutputStream;
import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.outgoing.database.DownloadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.domain.download.DownloadFileNames;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 긴 I/O 동안 DB 커넥션을 잡지 않도록 트랜잭션 없이 돌고, 상태 전이만 DownloadJobTransitions 에서 짧게 커밋한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadCompressionWorker {

    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final DownloadJobTransitions downloadJobTransitions;
    private final DownloadJobRepository downloadJobRepository;
    private final DownloadFileRepository downloadFileRepository;
    private final FileStorage fileStorage;

    private final DownloadProcessingMetrics metrics;

    public void compress(Long jobId) {
        Timer.Sample sample = metrics.start();
        boolean success = false;
        try {
            success = compressAttempt(jobId);
        } finally {
            metrics.completed(sample, success);
        }
    }

    private boolean compressAttempt(Long jobId) {
        DownloadJob job = downloadJobTransitions.markRunning(jobId);
        metrics.startedAt(job.getCreatedAt());
        StorageKey zipStorageKey = new StorageKey("rooms/%d/downloads/%d.zip".formatted(job.getRoomId(), jobId));

        // 스토리지 연결 실패도 FAILED 로 넘기려고 try 안에서 연다. 밖에 두면 잡이 RUNNING 에 남는다.
        AbortableOutputStream out = null;
        try {
            List<StoredFile> targets = loadTargets(jobId);
            out = fileStorage.openUploadStream(zipStorageKey, ZIP_CONTENT_TYPE);
            writeZip(out, targets, jobId);
            downloadJobTransitions.markReady(jobId, zipStorageKey);
            return true;
        } catch (Exception e) {
            if (out != null) {
                out.abort();
            }
            // 잡 행의 사유 한 줄로는 원인을 가릴 수 없어 스택트레이스를 남긴다.
            log.error("zip 압축에 실패했습니다. jobId={}", jobId, e);
            downloadJobTransitions.markFailed(jobId, failureReasonOf(e));
            return false;
        }
    }

    private List<StoredFile> loadTargets(Long jobId) {
        List<Long> mediaIds = downloadJobRepository.findMediaIdsByJobId(jobId);
        return downloadFileRepository.findAllByIdIn(mediaIds);
    }

    // 실패 때 close() 가 미완성 업로드를 완료하려 하므로 try-with-resources 를 쓰지 않는다. 실패는 호출부가 abort() 한다.
    private void writeZip(AbortableOutputStream out, List<StoredFile> targets, Long jobId) throws IOException {
        long totalBytes = targets.stream().mapToLong(file -> file.getFileSize().bytes()).sum();
        List<String> entryNames =
            DownloadFileNames.deduplicate(targets.stream().map(StoredFile::getOriginalFileName).toList());

        ZipOutputStream zipOut = new ZipOutputStream(out);
        long processedBytes = 0;
        int lastReportedProgress = -1;
        for (int i = 0; i < targets.size(); i++) {
            StoredFile file = targets.get(i);
            zipOut.putNextEntry(new ZipEntry(entryNames.get(i)));
            try (InputStream in = fileStorage.openDownloadStream(file.getStorageKey())) {
                in.transferTo(zipOut);
            }
            zipOut.closeEntry();

            processedBytes += file.getFileSize().bytes();
            int progress = totalBytes == 0 ? 100 : (int) (processedBytes * 100 / totalBytes);
            if (progress != lastReportedProgress) {
                downloadJobTransitions.updateProgress(jobId, progress);
                lastReportedProgress = progress;
            }
        }
        zipOut.close();
    }

    private String failureReasonOf(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
