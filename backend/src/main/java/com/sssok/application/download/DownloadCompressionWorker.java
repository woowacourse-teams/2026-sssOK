package com.sssok.application.download;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.file.DownloadFileNames;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 실제 비동기 진입점은 이 워커를 호출하는 DownloadJobEventListener 쪽 @Async
// 압축은 방 크기에 따라 오래 걸리는 I/O라 트랜잭션(따라서 DB 커넥션)을 그 시간만큼 붙들면 안 된다.
// 상태 전이는 DownloadJobTransitions (별도 빈)에 위임해 그때그때 짧게 커밋한다.
@Service
@RequiredArgsConstructor
public class DownloadCompressionWorker {

    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final DownloadJobTransitions downloadJobTransitions;
    private final DownloadJobRepository downloadJobRepository;
    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;

    public void compress(Long jobId) {
        DownloadJob job = downloadJobTransitions.markRunning(jobId);
        StorageKey zipStorageKey = new StorageKey("rooms/%d/downloads/%d.zip".formatted(job.getRoomId(), jobId));

        // openUploadStream(스토리지 연결) 자체도 실패할 수 있어 try 안에 둔다
        // 밖에 두면 그 실패가 그대로 전파돼 잡이 RUNNING에서 멈춘 채 영영 FAILED로 못 넘어간다.
        AbortableOutputStream out = null;
        try {
            List<StoredFile> targets = loadTargets(jobId);
            out = fileStoragePort.openUploadStream(zipStorageKey, ZIP_CONTENT_TYPE);
            writeZip(out, targets, jobId);
            downloadJobTransitions.markReady(jobId, zipStorageKey);
        } catch (Exception e) {
            if (out != null) {
                out.abort();
            }
            downloadJobTransitions.markFailed(jobId, failureReasonOf(e));
        }
    }

    private List<StoredFile> loadTargets(Long jobId) {
        List<Long> mediaIds = downloadJobRepository.findMediaIdsByJobId(jobId);
        return fileRepository.findAllByIdIn(mediaIds);
    }

    // out은 성공 시에만 close() 한다 실패해서 예외로 빠지면 compress()의 catch가 close() 대신 abort()를 부른다.
    // 그래서 여기서 try-with-resources로 out을 감싸지 않는다(예외가 나도 try-with-resources는 close()를 불러버려서 미완성 업로드를 완료 처리하려다 실패한다).
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
            try (InputStream in = fileStoragePort.openDownloadStream(file.getStorageKey())) {
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
