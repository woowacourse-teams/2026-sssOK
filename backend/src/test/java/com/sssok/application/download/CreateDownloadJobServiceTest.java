package com.sssok.application.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.download.exception.DownloadRateLimitedException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.MediaSelection;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.config.DownloadProperties;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CreateDownloadJobServiceTest extends PostgresContainerSupport {

    @Autowired
    CreateDownloadJobService createDownloadJobService;

    @Autowired
    DownloadProperties downloadProperties;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    private Long media(long roomId, UploadStatus status) {
        StoredFile file = StoredFile.reserve(roomId, 1L, "test.jpg", "image/jpeg", new FileSize(1024), Instant.now());
        if (status == UploadStatus.PROCESSING || status == UploadStatus.READY) {
            file.startProcessing();
        }
        if (status == UploadStatus.READY) {
            file.markReady();
        }
        return fileRepository.save(file).getId();
    }

    @Test
    void QUEUED_상태의_잡을_만들고_대상을_함께_저장한다() {
        Long media1 = media(1L, UploadStatus.READY);
        Long media2 = media(1L, UploadStatus.READY);

        CreateDownloadJobResult result =
            createDownloadJobService.create(1L, 100L, MediaSelection.include(List.of(media1, media2)), null, MediaUploaderFilter.ALL);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.QUEUED);
        assertThat(result.mediaCount()).isEqualTo(2);
        assertThat(result.totalSize()).isEqualTo(2048L);
        assertThat(result.fileName()).isEqualTo("sssOK_1.zip");
        assertThat(downloadJobRepository.findMediaIdsByJobId(result.jobId()))
            .containsExactlyInAnyOrder(media1, media2);
    }

    // 대상은 잡을 만들 때 job_media 에 고정된다 — 여기서 빠지면 압축 시점에 READY 가 돼도 못 들어간다.
    @Test
    void 썸네일이_아직_없는_미디어도_대상에_고정한다() {
        Long ready = media(1L, UploadStatus.READY);
        Long processing = media(1L, UploadStatus.PROCESSING);

        CreateDownloadJobResult result =
            createDownloadJobService.create(1L, 100L, MediaSelection.include(List.of(ready, processing)), null, MediaUploaderFilter.ALL);

        assertThat(result.mediaCount()).isEqualTo(2);
        assertThat(downloadJobRepository.findMediaIdsByJobId(result.jobId()))
            .containsExactlyInAnyOrder(ready, processing);
    }

    @Test
    void 대상이_없으면_리졸버의_예외가_그대로_전파된다() {
        assertThatThrownBy(() -> createDownloadJobService.create(1L, 100L, MediaSelection.include(List.of(999_999L)), null, MediaUploaderFilter.ALL))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 동시_진행_중인_잡이_상한에_도달하면_429() {
        Long media = media(1L, UploadStatus.READY);
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        for (int i = 0; i < maxJobs; i++) {
            createDownloadJobService.create(1L, 100L, MediaSelection.include(List.of(media)), null, MediaUploaderFilter.ALL);
        }

        assertThatThrownBy(() -> createDownloadJobService.create(1L, 100L, MediaSelection.include(List.of(media)), null, MediaUploaderFilter.ALL))
            .isInstanceOf(DownloadRateLimitedException.class);
    }

    @Test
    void 다른_요청자의_진행_중인_잡은_카운트에_영향을_주지_않는다() {
        Long media = media(1L, UploadStatus.READY);
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        for (int i = 0; i < maxJobs; i++) {
            createDownloadJobService.create(1L, 100L, MediaSelection.include(List.of(media)), null, MediaUploaderFilter.ALL);
        }

        CreateDownloadJobResult result = createDownloadJobService.create(
            1L, 200L, MediaSelection.include(List.of(media)), null, MediaUploaderFilter.ALL);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.QUEUED);
    }
}
