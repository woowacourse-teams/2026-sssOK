package com.sssok.application.download;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.download.exception.DownloadRateLimitedException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import com.sssok.infrastructure.config.DownloadProperties;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
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

    @Autowired
    MemberRepository memberRepository;

    @MockitoBean
    DownloadCompressionWorker downloadCompressionWorker;

    private Long member(String nickname) {
        return memberRepository.save(Member.register(new Nickname(nickname), Instant.now())).getId();
    }

    private Long media(long roomId, UploadStatus status) {
        StoredFile file = StoredFile.reserve(roomId, 1L, "test.jpg", "image/jpeg", new FileSize(1024), Instant.now(), SIZE_POLICY);
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
        Long requesterId = member("생성요청자");

        CreateDownloadJobResult result =
            createDownloadJobService.create(1L, requesterId, List.of(media1, media2), null, null);

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
        Long requesterId = member("대상요청자");

        CreateDownloadJobResult result =
            createDownloadJobService.create(1L, requesterId, List.of(ready, processing), null, null);

        assertThat(result.mediaCount()).isEqualTo(2);
        assertThat(downloadJobRepository.findMediaIdsByJobId(result.jobId()))
            .containsExactlyInAnyOrder(ready, processing);
    }

    @Test
    void 대상이_없으면_리졸버의_예외가_그대로_전파된다() {
        Long requesterId = member("빈대상요청자");

        assertThatThrownBy(() -> createDownloadJobService.create(
            1L, requesterId, List.of(999_999L), null, null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 존재하지_않는_요청자는_잡을_생성할_수_없다() {
        Long media = media(1L, UploadStatus.READY);

        assertThatThrownBy(() -> createDownloadJobService.create(
            1L, 999_999L, List.of(media), null, null))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 동시_진행_중인_잡이_상한에_도달하면_429() {
        Long media = media(1L, UploadStatus.READY);
        Long requesterId = member("제한요청자");
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        for (int i = 0; i < maxJobs; i++) {
            createDownloadJobService.create(1L, requesterId, List.of(media), null, null);
        }

        assertThatThrownBy(() -> createDownloadJobService.create(
            1L, requesterId, List.of(media), null, null))
            .isInstanceOf(DownloadRateLimitedException.class);
    }

    @Test
    void 다른_요청자의_진행_중인_잡은_카운트에_영향을_주지_않는다() {
        Long media = media(1L, UploadStatus.READY);
        Long firstRequesterId = member("첫요청자");
        Long secondRequesterId = member("둘째요청자");
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        for (int i = 0; i < maxJobs; i++) {
            createDownloadJobService.create(1L, firstRequesterId, List.of(media), null, null);
        }

        CreateDownloadJobResult result = createDownloadJobService.create(
            1L, secondRequesterId, List.of(media), null, null);

        assertThat(result.status()).isEqualTo(DownloadJobStatus.QUEUED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 같은_요청자의_동시_요청도_진행_중인_잡_상한을_넘지_않는다() throws Exception {
        Long media = media(1L, UploadStatus.READY);
        Long requesterId = member("동시요청자");
        int requestCount = 10;
        int maxJobs = downloadProperties.maxConcurrentJobsPerRequester();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        createDownloadJobService.create(
                            1L, requesterId, List.of(media), null, null);
                        return true;
                    } catch (DownloadRateLimitedException exception) {
                        return false;
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long createdCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    createdCount++;
                }
            }

            assertThat(createdCount).isEqualTo(maxJobs);
            assertThat(downloadJobRepository.countByRequesterIdAndStatusIn(
                requesterId, List.of(DownloadJobStatus.QUEUED, DownloadJobStatus.RUNNING)))
                .isEqualTo(maxJobs);
        } finally {
            executor.shutdownNow();
        }
    }
}
