package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 스토리지는 목으로 둔다 — 워커를 직접(동기) 호출한다.
// 비동기 배선 자체는 DownloadJobAsyncTriggerTest 에서 별도로 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DownloadCompressionWorkerTest {

    private static final Long ROOM_ID = 1L;
    private static final Long REQUESTER_ID = 100L;

    @Autowired
    DownloadCompressionWorker downloadCompressionWorker;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    private Long media(String fileName, byte[] content) {
        StoredFile file = StoredFile.reserve(
            ROOM_ID, 1L, fileName, "image/jpeg", new FileSize(content.length), Instant.now());
        Long mediaId = fileRepository.save(file).getId();
        given(fileStoragePort.openDownloadStream(eq(file.getStorageKey())))
            .willReturn(new ByteArrayInputStream(content));
        return mediaId;
    }

    private DownloadJob job(List<Long> mediaIds, long totalSize) {
        DownloadJob job = downloadJobRepository.save(
            DownloadJob.create(ROOM_ID, REQUESTER_ID, mediaIds.size(), totalSize, "sssOK_1.zip", Instant.now()));
        downloadJobRepository.saveJobMedia(job.getId(), mediaIds);
        return job;
    }

    @Test
    void 성공하면_모든_파일을_zip으로_묶어_올리고_READY로_전이한다() throws IOException {
        byte[] contentA = "hello-a".getBytes(StandardCharsets.UTF_8);
        byte[] contentB = "hello-b".getBytes(StandardCharsets.UTF_8);
        Long mediaA = media("a.jpg", contentA);
        Long mediaB = media("b.jpg", contentB);
        DownloadJob job = job(List.of(mediaA, mediaB), contentA.length + contentB.length);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        given(fileStoragePort.openUploadStream(any(), eq("application/zip")))
            .willReturn(capturing(captured));

        downloadCompressionWorker.compress(job.getId());

        DownloadJob updated = downloadJobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DownloadJobStatus.READY);
        assertThat(updated.getProgress()).isEqualTo(100);
        assertThat(updated.getZipStorageKey()).isNotNull();
        assertThat(zipEntries(captured.toByteArray())).containsEntry("a.jpg", "hello-a")
            .containsEntry("b.jpg", "hello-b");
    }

    @Test
    void 이름이_겹치면_zip_안에서_접미사가_붙는다() throws IOException {
        byte[] contentA = "first".getBytes(StandardCharsets.UTF_8);
        byte[] contentB = "second".getBytes(StandardCharsets.UTF_8);
        Long mediaA = media("cat.jpg", contentA);
        Long mediaB = media("cat.jpg", contentB);
        DownloadJob job = job(List.of(mediaA, mediaB), contentA.length + contentB.length);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        given(fileStoragePort.openUploadStream(any(), eq("application/zip")))
            .willReturn(capturing(captured));

        downloadCompressionWorker.compress(job.getId());

        assertThat(zipEntries(captured.toByteArray())).containsKeys("cat.jpg", "cat (1).jpg");
    }

    @Test
    void 압축_도중_실패하면_업로드를_abort하고_FAILED로_전이한다() {
        Long media = media("a.jpg", "hello".getBytes(StandardCharsets.UTF_8));
        DownloadJob job = job(List.of(media), 5L);

        AtomicBoolean aborted = new AtomicBoolean(false);
        given(fileStoragePort.openUploadStream(any(), eq("application/zip")))
            .willReturn(new AbortableOutputStream() {
                @Override
                public void write(int b) {
                    throw new UncheckedIOException(new IOException("업로드 실패"));
                }

                @Override
                public void abort() {
                    aborted.set(true);
                }
            });

        downloadCompressionWorker.compress(job.getId());

        DownloadJob updated = downloadJobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DownloadJobStatus.FAILED);
        assertThat(updated.getFailureReason()).isNotNull();
        assertThat(aborted.get()).isTrue();
    }

    @Test
    void 스토리지_연결_자체가_실패해도_FAILED로_전이한다() {
        Long media = media("a.jpg", "hello".getBytes(StandardCharsets.UTF_8));
        DownloadJob job = job(List.of(media), 5L);

        given(fileStoragePort.openUploadStream(any(), anyString()))
            .willThrow(new RuntimeException("R2 연결 실패"));

        downloadCompressionWorker.compress(job.getId());

        DownloadJob updated = downloadJobRepository.findById(job.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DownloadJobStatus.FAILED);
    }

    @Test
    void 없는_잡을_압축하려_하면_예외() {
        assertThatThrownBy(() -> downloadCompressionWorker.compress(999L))
            .isInstanceOf(IllegalStateException.class);
    }

    private AbortableOutputStream capturing(ByteArrayOutputStream captured) {
        return new AbortableOutputStream() {
            @Override
            public void write(int b) {
                captured.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                captured.write(b, off, len);
            }

            @Override
            public void abort() {
            }
        };
    }

    private Map<String, String> zipEntries(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
