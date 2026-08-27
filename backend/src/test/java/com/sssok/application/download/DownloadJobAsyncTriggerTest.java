package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sssok.application.port.out.AbortableOutputStream;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 일부러 @Transactional을 안 쓴다 — CreateDownloadJobService가 QUEUED로 잡을 만드는 트랜잭션이
// 실제로 커밋돼야 @TransactionalEventListener(AFTER_COMMIT)가 발화하고, 그래야 별도 스레드(media-worker)에서
// 도는 DownloadJobEventListener/DownloadCompressionWorker까지 실제로 확인할 수 있다.
// 테스트가 끝나면 데이터를 직접 지운다(자동 롤백이 없으므로).
@SpringBootTest
@ActiveProfiles("test")
class DownloadJobAsyncTriggerTest {

    private static final Long ROOM_ID = 1L;
    private static final Long REQUESTER_ID = 100L;

    @Autowired
    CreateDownloadJobService createDownloadJobService;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from download_job_media");
        jdbcTemplate.update("delete from download_job");
        jdbcTemplate.update("delete from stored_file");
    }

    private Long media(Long roomId, byte[] content) {
        StoredFile file = StoredFile.reserve(roomId, 1L, "test.jpg", "image/jpeg", new FileSize(content.length), Instant.now());
        file.startProcessing();
        file.markReady();
        given(fileStoragePort.openDownloadStream(eq(file.getStorageKey())))
            .willReturn(new ByteArrayInputStream(content));
        return fileRepository.save(file).getId();
    }

    @Test
    void 잡을_생성하면_트랜잭션_커밋_후_비동기로_압축까지_끝나_READY가_된다() throws InterruptedException {
        Long media = media(ROOM_ID, "hello".getBytes());
        given(fileStoragePort.openUploadStream(any(), eq("application/zip")))
            .willReturn(discardingStream());

        CreateDownloadJobResult result =
            createDownloadJobService.create(ROOM_ID, REQUESTER_ID, List.of(media), null);

        DownloadJob job = awaitStatus(result.jobId(), DownloadJobStatus.READY);
        assertThat(job.getStatus()).isEqualTo(DownloadJobStatus.READY);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getZipStorageKey()).isNotNull();
    }

    private AbortableOutputStream discardingStream() {
        return new AbortableOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public void abort() {
            }
        };
    }

    private DownloadJob awaitStatus(Long jobId, DownloadJobStatus expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            DownloadJob job = downloadJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == expected) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("5초 안에 " + expected + " 상태가 되지 않았다");
    }
}
