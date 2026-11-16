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
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@H2IntegrationTest
@TestPropertySource(properties = {
    "download.compression.workers=1",
    "download.compression.queue-capacity=0"
})
class DownloadCompressionRejectionTest {

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
    MeterRegistry meterRegistry;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStorage fileStorage;

    private final CountDownLatch occupied = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private Long requesterId;

    @BeforeEach
    void setUp() {
        UploadRoomFixtures.ensure(jdbcTemplate, ROOM_ID);
        requesterId = memberRepository.save(Member.register(new Nickname("다운로더"), Instant.now())).getId();
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        release.countDown();
        Thread.sleep(200);
        jdbcTemplate.update("delete from download_job_media");
        jdbcTemplate.update("delete from download_job");
        jdbcTemplate.update("delete from stored_file");
        jdbcTemplate.update("delete from member where id = ?", requesterId);
    }

    @Test
    void 압축_실행기가_가득_차면_잡을_바로_실패로_닫고_사유를_남긴다() throws InterruptedException {
        given(fileStorage.openUploadStream(any(), eq("application/zip"))).willAnswer(invocation -> {
            occupied.countDown();
            release.await(5, TimeUnit.SECONDS);
            return discardingStream();
        });
        double rejectedBefore = rejectedCount();

        Long first = createJob();
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        Long second = createJob();

        DownloadJob rejected = downloadJobRepository.findById(second).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(FailedDownloadJobStatus.INSTANCE);
        assertThat(rejected.getFailureReason()).isEqualTo(DownloadCompressionDispatcher.REJECTED_REASON);
        assertThat(rejectedCount()).isEqualTo(rejectedBefore + 1);
        assertThat(downloadJobRepository.findById(first).orElseThrow().getStatus())
            .isEqualTo(RunningDownloadJobStatus.INSTANCE);

        long active = downloadJobRepository.countByRequesterIdAndStatusIn(requesterId, DownloadJobStatus.active());
        assertThat(active).isEqualTo(1);
        release.countDown();
        assertThat(awaitStatus(first, ReadyDownloadJobStatus.INSTANCE).getStatus())
            .isEqualTo(ReadyDownloadJobStatus.INSTANCE);
    }

    private Long createJob() {
        Long media = media("hello".getBytes());
        return createDownloadJobService.create(
            new DownloadTargetCommand(ROOM_ID, requesterId, List.of(media), null, null)).jobId();
    }

    private Long media(byte[] content) {
        StoredFile file = StoredFile.reserve(ROOM_ID, requesterId, "test.jpg", "image/jpeg",
            new FileSize(content.length), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        file.markReady();
        given(fileStorage.openDownloadStream(eq(file.getStorageKey())))
            .willReturn(new ByteArrayInputStream(content));
        return fileRepository.save(file).getId();
    }

    private double rejectedCount() {
        return meterRegistry.counter("sssok.download.rejected").count();
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
