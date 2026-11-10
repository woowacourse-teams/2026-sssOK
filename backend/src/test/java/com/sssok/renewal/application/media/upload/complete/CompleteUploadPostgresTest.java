package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.exception.IllegalUploadStatusException;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.notification.send.UploadNotificationService;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * PostgreSQL의 행 잠금 경합을 실제 서비스·저장소로 검증한다.
 * 일반 등록·롤백 계약은 H2, 저장소 SDK와 HEAD 풀은 각각의 테스트에서 검증한다.
 */
@PostgresIntegrationTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "media.thumbnail.auto-generate=false",
    "media.search.enabled=false"
})
class CompleteUploadPostgresTest extends PostgresContainerSupport {

    @Autowired
    CompleteUploadService service;
    @Autowired
    UploadFileRepository files;
    @Autowired
    AnonymousAuthService auth;
    @Autowired
    CreateRoomService rooms;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    UploadNotificationService notification;
    @MockitoBean
    FileStorage storage;

    long owner;

    @BeforeEach
    void setUp() {
        given(storage.findUploaded(any())).willReturn(Optional.of(new FileStorage.UploadedObject(1024, "image/jpeg")));
        given(storage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn("https://storage.example.com/read");
        owner = auth.authenticate("등록검증").userId();
    }

    @Test
    void 순서가_다른_동시_등록은_교착_없이_하나만_성공한다() throws Exception {
        Reserved reserved = reserve(10);
        List<Long> reversed = new ArrayList<>(reserved.ids());
        Collections.reverse(reversed);

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> forward = callers.submit(registerWhenStarted(start, command(reserved)));
            Future<Boolean> backward = callers.submit(registerWhenStarted(
                start, new CompleteUploadCommand(reserved.room(), owner, reversed)));
            start.countDown();

            assertThat(List.of(forward.get(15, TimeUnit.SECONDS), backward.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        } finally {
            callers.shutdownNow();
        }

        assertThat(count("room_events where room_id = ? and event_type = 'media.created'", reserved.room()))
            .isEqualTo(10);
        assertThat(count("thumbnail_job where media_id in (select id from stored_file where room_id = ?)",
            reserved.room())).isEqualTo(10);
    }

    private Callable<Boolean> registerWhenStarted(CountDownLatch start, CompleteUploadCommand command) {
        return () -> {
            start.await();
            try {
                service.complete(command);
                return true;
            } catch (IllegalUploadStatusException e) {
                return false;
            }
        };
    }

    private record Reserved(long room, List<Long> ids) { }

    private Reserved reserve(int count) {
        long room = rooms.create(owner, "등록 검증 방", null).room().getId();
        List<StoredFile> reserved = IntStream.range(0, count)
            .mapToObj(i -> reserveFile(room, owner, "photo-" + i + ".jpg"))
            .toList();
        List<Long> ids = files.savePendingUploads(reserved).stream().map(StoredFile::getId).toList();
        return new Reserved(room, ids);
    }

    private StoredFile reserveFile(long room, long uploader, String name) {
        return StoredFile.reserve(room, uploader, name, "image/jpeg", new FileSize(1024), Instant.now(), SIZE_POLICY);
    }

    private CompleteUploadCommand command(Reserved reserved) {
        return new CompleteUploadCommand(reserved.room(), owner, reserved.ids());
    }

    private long count(String fromAndWhere, Object... args) {
        return jdbc.queryForObject("select count(*) from " + fromAndWhere, Long.class, args);
    }

}
