package com.sssok.renewal.application.download.zip;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sssok.renewal.application.download.shared.DownloadTargetCommand;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// #582 zip 의 트랜잭션·비동기·정리 경계 계약. API 로는 만들 수 없는 상황(롤백, 정리 중 일부 실패)을 직접 만든다.
// 트랜잭션을 걸지 않는다 — 커밋 여부에 따라 비동기 워커가 도는지를 봐야 한다.
// 스프링 컨텍스트를 DownloadApiTest 와 공유하도록 같은 설정을 쓴다.
@Tag("integration-postgres")
@SpringBootTest(properties = {
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU4MQ==",
    "download.presigned-get-ttl=5m"
})
class ZipDownloadBoundaryTest extends PostgresContainerSupport {

    private static final Duration RETENTION = Duration.ofHours(1);

    @Autowired
    CreateDownloadJobService createDownloadJobService;

    @Autowired
    SweepDownloadJobsService sweepDownloadJobsService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStorage fileStorage;

    private Long requesterId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        AuthResult requester = anonymousAuthService.authenticate("가현");
        requesterId = requester.userId();
        roomId = createRoomService.create(requesterId, "제주 여행", null).room().getId();
    }

    private Long media() {
        StoredFile file = StoredFile.reserve(
            roomId, requesterId, "a.jpg", "image/jpeg", new FileSize(1), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        file.markReady();
        return fileRepository.save(file).getId();
    }

    private Long insertJob(String status, Instant readyAt, String zipKey) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO download_job (room_id, requester_id, status, media_count, total_size_bytes, file_name,
                zip_storage_key, progress, ready_at, failure_reason, created_at, updated_at)
            VALUES (?, ?, ?, 1, 1, 'sssOK.zip', ?, ?, ?, NULL, ?, ?) RETURNING id
            """, Long.class, roomId, requesterId, status, zipKey, "READY".equals(status) ? 100 : 0,
            readyAt == null ? null : Timestamp.from(readyAt), Timestamp.from(now), Timestamp.from(now));
    }

    private String statusOf(Long jobId) {
        return jdbcTemplate.queryForObject("SELECT status FROM download_job WHERE id = ?", String.class, jobId);
    }

    @Test
    void 잡_생성_트랜잭션이_롤백되면_잡도_남지_않고_압축_워커도_돌지_않는다() throws InterruptedException {
        Long mediaId = media();

        transactionTemplate.executeWithoutResult(status -> {
            createDownloadJobService.create(new DownloadTargetCommand(
                roomId, requesterId, List.of(mediaId), null, null));
            status.setRollbackOnly();
        });
        Thread.sleep(1000);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM download_job WHERE requester_id = ?", Long.class, requesterId)).isZero();
        verify(fileStorage, never()).openUploadStream(any(), anyString());
    }

    @Test
    void 보관_기간이_지난_READY_잡은_zip_을_지우고_EXPIRED_로_바꾸며_한_잡이_실패해도_나머지를_계속_정리한다() {
        Instant now = Instant.now();
        Instant expiredReadyAt = now.minus(RETENTION).minusSeconds(60);
        String failingKey = "rooms/%d/downloads/failing.zip".formatted(roomId);
        String okKey = "rooms/%d/downloads/ok.zip".formatted(roomId);
        String recentKey = "rooms/%d/downloads/recent.zip".formatted(roomId);
        Long failing = insertJob("READY", expiredReadyAt, failingKey);
        Long expired = insertJob("READY", expiredReadyAt, okKey);
        Long recent = insertJob("READY", now.minusSeconds(60), recentKey);
        Long running = insertJob("RUNNING", null, null);
        willThrow(new IllegalStateException("스토리지 장애")).given(fileStorage).delete(new StorageKey(failingKey));

        int swept = sweepDownloadJobsService.sweepExpired(now);

        assertThat(swept).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(expired)).isEqualTo("EXPIRED");
        assertThat(statusOf(failing)).isEqualTo("READY");
        assertThat(statusOf(recent)).isEqualTo("READY");
        assertThat(statusOf(running)).isEqualTo("RUNNING");
        verify(fileStorage).delete(new StorageKey(okKey));
        verify(fileStorage).delete(new StorageKey(failingKey));
        verify(fileStorage, never()).delete(new StorageKey(recentKey));
    }

    @Test
    void 정리에_실패한_잡은_다음_회차에_다시_정리된다() {
        Instant now = Instant.now();
        String key = "rooms/%d/downloads/retry.zip".formatted(roomId);
        Long job = insertJob("READY", now.minus(RETENTION).minusSeconds(60), key);
        willThrow(new IllegalStateException("스토리지 장애")).given(fileStorage).delete(new StorageKey(key));
        sweepDownloadJobsService.sweepExpired(now);
        assertThat(statusOf(job)).isEqualTo("READY");

        org.mockito.Mockito.reset(fileStorage);
        sweepDownloadJobsService.sweepExpired(now);

        assertThat(statusOf(job)).isEqualTo("EXPIRED");
    }
}
