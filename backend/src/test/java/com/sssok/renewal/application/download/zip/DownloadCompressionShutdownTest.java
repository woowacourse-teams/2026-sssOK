package com.sssok.renewal.application.download.zip;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.download.shared.DownloadTargetCommand;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.domain.download.status.DownloadJobStatus;
import com.sssok.renewal.domain.download.status.FailedDownloadJobStatus;
import com.sssok.renewal.domain.download.status.ReadyDownloadJobStatus;
import com.sssok.renewal.domain.download.status.RunningDownloadJobStatus;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.storage.AbortableOutputStream;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.H2IntegrationTest;
import com.sssok.support.UploadRoomFixtures;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 컨텍스트를 멈춰 종료 단계를 그대로 돌리므로 테스트가 끝나면 컨텍스트를 버린다.
@H2IntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "download.compression.workers=1",
    "download.compression.queue-capacity=5",
    "download.compression.prefetch-depth=0",
    "shutdown.task-drain-timeout=200ms"
})
class DownloadCompressionShutdownTest {

    private static final Long ROOM_ID = 1L;

    @Autowired
    CreateDownloadJobService createDownloadJobService;

    @Autowired
    DownloadJobRepository downloadJobRepository;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ConfigurableApplicationContext context;

    @MockitoBean
    FileStorage fileStorage;

    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch neverReleased = new CountDownLatch(1);
    private final AtomicInteger aborted = new AtomicInteger();
    private Long requesterId;

    @BeforeEach
    void setUp() {
        UploadRoomFixtures.ensure(jdbcTemplate, ROOM_ID);
        requesterId = memberRepository.save(Member.register(new Nickname("다운로더"), Instant.now())).getId();
        given(fileStorage.openUploadStream(any(), eq("application/zip"))).willAnswer(invocation -> recordingStream());
    }

    @Test
    void 종료_대기_시간_안에_끝나지_못한_압축_잡은_대기열과_실행_중_모두_실패로_닫힌다() throws InterruptedException {
        Long finished = createJob(media());
        assertThat(awaitStatus(finished, ReadyDownloadJobStatus.INSTANCE).getStatus())
            .isEqualTo(ReadyDownloadJobStatus.INSTANCE);

        Long running = createJob(blockingMedia());
        assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();
        Long queued1 = createJob(media());
        Long queued2 = createJob(media());
        assertThat(status(running)).isEqualTo(RunningDownloadJobStatus.INSTANCE);

        long startedAt = System.nanoTime();
        context.stop();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        for (Long jobId : List.of(running, queued1, queued2)) {
            DownloadJob job = downloadJobRepository.findById(jobId).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(FailedDownloadJobStatus.INSTANCE);
            assertThat(job.getFailureReason()).isEqualTo(DownloadCompressionWorker.INTERRUPTED_REASON);
        }
        assertThat(downloadJobRepository.countByRequesterIdAndStatusIn(requesterId, DownloadJobStatus.active()))
            .isZero();
        assertThat(status(finished)).isEqualTo(ReadyDownloadJobStatus.INSTANCE);
        assertThat(aborted).hasValue(1);
        // 끊긴 워커를 기다리는 시간(3초)까지 쓰지 않고 바로 끝난다.
        assertThat(elapsedMillis).isLessThan(3_000);
    }

    private Long createJob(Long mediaId) {
        return createDownloadJobService.create(
            new DownloadTargetCommand(ROOM_ID, requesterId, List.of(mediaId), null, null)).jobId();
    }

    private Long media() {
        StoredFile file = storedFile();
        given(fileStorage.openDownloadStream(eq(file.getStorageKey())))
            .willAnswer(invocation -> new ByteArrayInputStream("hello".getBytes()));
        return fileRepository.save(file).getId();
    }

    // 원본을 받는 동안 멈춰 있다가 중단 요청에만 풀린다. 응답하지 않는 스토리지 대신이다.
    private Long blockingMedia() {
        StoredFile file = storedFile();
        given(fileStorage.openDownloadStream(eq(file.getStorageKey()))).willAnswer(invocation -> {
            blocked.countDown();
            neverReleased.await();
            return new ByteArrayInputStream("hello".getBytes());
        });
        return fileRepository.save(file).getId();
    }

    private StoredFile storedFile() {
        StoredFile file = StoredFile.reserve(ROOM_ID, requesterId, "test.jpg", "image/jpeg",
            new FileSize(5), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        file.markReady();
        return file;
    }

    private AbortableOutputStream recordingStream() {
        return new AbortableOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public void abort() {
                aborted.incrementAndGet();
            }
        };
    }

    private DownloadJobStatus status(Long jobId) {
        return downloadJobRepository.findById(jobId).orElseThrow().getStatus();
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
