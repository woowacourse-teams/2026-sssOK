package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.download.exception.DownloadExpiredException;
import com.sssok.application.download.exception.DownloadForbiddenException;
import com.sssok.application.download.exception.DownloadNotFoundException;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.StorageKey;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지 서명은 목으로 둔다.
@SpringBootTest
@ActiveProfiles("test")
class GetDownloadJobStatusServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long REQUESTER_ID = 100L;
    private static final String PRESIGNED = "https://storage.example.com/zip-signed";

    @Autowired
    GetDownloadJobStatusService getDownloadJobStatusService;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn(PRESIGNED);
    }

    private DownloadJob queuedJob() {
        return downloadJobRepository.save(
            DownloadJob.create(ROOM_ID, REQUESTER_ID, 3, 1000L, "sssOK_1.zip", Instant.now()));
    }

    private DownloadJob readyJob(Instant readyAt) {
        DownloadJob job = DownloadJob.create(ROOM_ID, REQUESTER_ID, 3, 1000L, "sssOK_1.zip", Instant.now());
        job.markRunning();
        job.markReady(new StorageKey("rooms/1/downloads/job-1.zip"), readyAt);
        return downloadJobRepository.save(job);
    }

    private DownloadJob failedJob() {
        DownloadJob job = DownloadJob.create(ROOM_ID, REQUESTER_ID, 3, 1000L, "sssOK_1.zip", Instant.now());
        job.markRunning();
        job.markFailed("스토리지 업로드 실패");
        return downloadJobRepository.save(job);
    }

    @Test
    void QUEUED_잡은_downloadUrl_없이_상태만_돌려준다() {
        DownloadJob job = queuedJob();

        GetDownloadJobStatusResult result = getDownloadJobStatusService.getStatus(job.getId(), REQUESTER_ID);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.QUEUED);
        assertThat(result.downloadUrl()).isNull();
        assertThat(result.expiresAt()).isNull();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void READY_잡은_다운로드_URL과_만료_시각을_돌려준다() {
        Instant readyAt = Instant.now();
        DownloadJob job = readyJob(readyAt);

        GetDownloadJobStatusResult result = getDownloadJobStatusService.getStatus(job.getId(), REQUESTER_ID);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.READY);
        assertThat(result.downloadUrl()).isEqualTo(PRESIGNED);
        assertThat(result.expiresAt()).isEqualTo(readyAt.plus(Duration.ofHours(1)));
        assertThat(result.progress()).isEqualTo(100);
    }

    @Test
    void FAILED_잡은_실패_사유를_돌려준다() {
        DownloadJob job = failedJob();

        GetDownloadJobStatusResult result = getDownloadJobStatusService.getStatus(job.getId(), REQUESTER_ID);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("스토리지 업로드 실패");
        assertThat(result.downloadUrl()).isNull();
    }

    @Test
    void 본인이_아니면_403() {
        DownloadJob job = queuedJob();

        assertThatThrownBy(() -> getDownloadJobStatusService.getStatus(job.getId(), 999L))
            .isInstanceOf(DownloadForbiddenException.class);
    }

    @Test
    void 없는_잡이면_404() {
        assertThatThrownBy(() -> getDownloadJobStatusService.getStatus(999L, REQUESTER_ID))
            .isInstanceOf(DownloadNotFoundException.class);
    }

    @Test
    void 보관_기간이_지난_READY_잡은_410() {
        DownloadJob job = readyJob(Instant.now().minus(Duration.ofHours(2)));

        assertThatThrownBy(() -> getDownloadJobStatusService.getStatus(job.getId(), REQUESTER_ID))
            .isInstanceOf(DownloadExpiredException.class);
    }
}
