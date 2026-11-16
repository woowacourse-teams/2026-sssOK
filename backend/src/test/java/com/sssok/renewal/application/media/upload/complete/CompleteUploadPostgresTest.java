package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.notification.send.UploadNotificationService;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * PostgreSQL의 행 잠금 경합을 실제 서비스·저장소로 검증한다.
 * 일반 등록·롤백 계약은 H2, 저장소 SDK와 HEAD 풀은 각각의 테스트에서 검증한다.
 */
@PostgresIntegrationTest
@Import(CompleteUploadPostgresTest.EventConfig.class)
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

    @Autowired
    UploadEvents events;
    @Autowired
    HikariDataSource dataSource;

    @TestConfiguration
    static class EventConfig {
        @Bean
        UploadEvents uploadEvents() {
            return new UploadEvents();
        }
    }

    static class UploadEvents {
        final ConcurrentLinkedQueue<MediaUploadedEvent> received = new ConcurrentLinkedQueue<>();
        @EventListener
        public void record(MediaUploadedEvent event) {
            received.add(event);
        }
    }

    long owner;

    @BeforeEach
    void setUp() {
        // 등록 2개와 AFTER_COMMIT SSE 저장의 REQUIRES_NEW 연결 2개를 확보한다.
        // 공통 테스트 기본 풀(2)의 고갈과 파일 행 잠금 검증을 분리한다.
        dataSource.setMaximumPoolSize(4);
        given(storage.findUploaded(any())).willReturn(Optional.of(new FileStorage.UploadedObject(1024, "image/jpeg")));
        given(storage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn("https://storage.example.com/read");
        owner = auth.authenticate("등록검증").userId();
    }

    @Test
    void 순서가_다른_동시_등록은_모두_성공하고_작업과_이벤트는_한번만_생긴다() throws Exception {
        Reserved reserved = reserve(3);
        List<Long> reversed = new ArrayList<>(reserved.ids());
        Collections.reverse(reversed);
        raceRegistrations(reserved, reserved.ids(), reversed);
        assertSideEffects(reserved, 3, 1);
    }

    @Test
    void 일부_ID가_겹친_동시_등록도_각_파일을_한번만_등록한다() throws Exception {
        Reserved reserved = reserve(3);
        raceRegistrations(reserved, reserved.ids().subList(0, 2), reserved.ids().subList(1, 3));
        assertSideEffects(reserved, 3, 2);
    }

    private void raceRegistrations(Reserved reserved, List<Long> firstIds, List<Long> secondIds) throws Exception {
        Long sharedId = firstIds.stream().filter(secondIds::contains).findFirst().orElseThrow();
        var sharedKey = files.findById(sharedId).orElseThrow().getStorageKey();
        CountDownLatch bothVerified = new CountDownLatch(2);
        given(storage.findUploaded(sharedKey)).willAnswer(call -> {
            bothVerified.countDown();
            assertThat(bothVerified.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(new FileStorage.UploadedObject(1024, "image/jpeg"));
        });
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CompleteUploadResult> first = callers.submit(registerWhenStarted(start,
                new CompleteUploadCommand(reserved.room(), owner, firstIds)));
            Future<CompleteUploadResult> second = callers.submit(registerWhenStarted(start,
                new CompleteUploadCommand(reserved.room(), owner, secondIds)));
            start.countDown();
            CompleteUploadResult firstResult = first.get(15, TimeUnit.SECONDS);
            CompleteUploadResult secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(firstResult.registered()).extracting(MediaDetail::mediaId).containsExactlyElementsOf(firstIds);
            assertThat(secondResult.registered()).extracting(MediaDetail::mediaId).containsExactlyElementsOf(secondIds);
            assertThat(firstResult.failed()).isEmpty();
            assertThat(secondResult.failed()).isEmpty();
        }
    }

    private void assertSideEffects(Reserved reserved, int fileCount, int uploadEvents) {
        assertThat(count("room_events where room_id = ? and event_type = 'media.created'", reserved.room()))
            .isEqualTo(fileCount);
        assertThat(count("thumbnail_job where media_id in (select id from stored_file where room_id = ?)",
            reserved.room())).isEqualTo(fileCount);
        assertThat(events.received.stream().filter(event -> event.roomId().equals(reserved.room())).count())
            .isEqualTo(uploadEvents);
    }

    private Callable<CompleteUploadResult> registerWhenStarted(CountDownLatch start, CompleteUploadCommand command) {
        return () -> {
            start.await();
            return service.complete(command);
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

    private long count(String fromAndWhere, Object... args) {
        return jdbc.queryForObject("select count(*) from " + fromAndWhere, Long.class, args);
    }

}
