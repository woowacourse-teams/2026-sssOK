package com.sssok.renewal.application.media.upload.reissue;

import javax.sql.DataSource;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.zaxxer.hikari.HikariDataSource;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.file.exception.UploadAlreadyCompletedException;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@PostgresIntegrationTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"
})
class ReissueUploadConcurrencyTest extends PostgresContainerSupport {
    @Autowired ReissueUploadUrlService service;
    @Autowired UploadFileRepository files;
    @Autowired AnonymousAuthService authentication;
    @Autowired CreateRoomService rooms;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    @MockitoBean FileStorage storage;
    @Autowired JdbcTemplate jdbcTemplate;
    private Long createdMediaId;

    @AfterEach
    void removeCreatedFile() {
        // 별도 트랜잭션으로 커밋한 동시성 테스트의 파일만 정리한다.
        if (createdMediaId != null) {
            jdbcTemplate.update("DELETE FROM stored_file WHERE id = ?", createdMediaId);
        }
    }

    @Test
    void 완료_등록이_커밋되면_대기하던_재발급은_최신_상태를_보고_거절한다() throws Exception {
        Long memberId = authentication.authenticate("재발급 동시성").userId();
        Long roomId = rooms.create(memberId, "동시성 검증", null).room().getId();
        StoredFile file = files.save(StoredFile.reserve(roomId, memberId, "a.jpg", "image/jpeg",
            new FileSize(1024), Instant.now().truncatedTo(ChronoUnit.MICROS), SIZE_POLICY));
        createdMediaId = file.getId();
        when(storage.presignPut(any(), anyString(), any(Duration.class))).thenReturn("signed-url");
        AtomicReference<Future<ReissuedUploadUrl>> reissue = new AtomicReference<>();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                // 완료 등록과 같은 파일 잠금·상태 전이를 커밋 직전에서 멈춘다.
                StoredFile lockedFile = files.findByIdForUpdate(file.getId()).orElseThrow();
                lockedFile.startProcessing();
                files.save(lockedFile);
                reissue.set(executor.submit(() -> service.reissue(
                    new ReissueUploadUrlCommand(roomId, file.getId(), memberId, null))));
                awaitBlockedReissue();
            });
            assertThatThrownBy(() -> reissue.get().get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(UploadAlreadyCompletedException.class);
        }
        StoredFile after = files.findById(file.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(UploadStatus.PROCESSING);
        assertThat(after.getRetryCount()).isZero();
        assertThat(after.getReservedAt()).isEqualTo(file.getReservedAt());
    }

    private void awaitBlockedReissue() {
        // 서비스용 두 연결을 점유하지 않는 관측 연결로 실제 DB 잠금 대기를 확인한다.
        HikariDataSource pool = (HikariDataSource) dataSource;
        try (Connection connection = DriverManager.getConnection(
                pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                 select count(*) from pg_stat_activity
                 where datname = current_database() and pid <> pg_backend_pid()
                   and wait_event_type = 'Lock' and query like '%stored_file%'
                 """)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(20);
            }
            throw new AssertionError("재발급 트랜잭션이 파일 잠금을 기다리지 않았습니다");
        } catch (Exception failure) {
            throw new AssertionError("DB 잠금 대기 관측 실패", failure);
        }
    }
}
