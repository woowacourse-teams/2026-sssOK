package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.file.exception.IllegalUploadStatusException;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.application.media.upload.exception.UploadVerificationUnavailableException;
import com.sssok.renewal.application.notification.send.UploadNotificationService;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 서비스·SDK·PostgreSQL 로 등록 경로를 검증한다. 저장소는 로컬 HTTP 서버가 HEAD 에 응답하는 것으로 대체한다.
 * H2 계약 테스트가 못 잡는 PostgreSQL 잠금 순서, 한 트랜잭션 롤백, 풀 상한, 저장소 지연 시 상태 유지를 본다.
 */
@PostgresIntegrationTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "media.thumbnail.auto-generate=false",
    "media.search.enabled=false",
    "media.upload.head.call-timeout=500ms"
})
class CompleteUploadPostgresTest extends PostgresContainerSupport {

    private static final String BUCKET_PATH = "/sssok-test/";
    private static final LocalHeadServer STORE = new LocalHeadServer();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("storage.r2.endpoint", STORE::endpoint);
    }

    @AfterAll
    static void closeStore() {
        STORE.close();
    }

    @Autowired CompleteUploadService service;
    @Autowired UploadFileRepository files;
    @Autowired AnonymousAuthService auth;
    @Autowired CreateRoomService rooms;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UploadNotificationService notification;

    long owner;

    @BeforeEach
    void setUp() {
        STORE.awaitIdle();
        STORE.reset();
        owner = auth.authenticate("등록검증").userId();
    }

    @Test
    void 일부_파일이_거절돼도_나머지를_요청_순서대로_등록한다() {
        Reserved reserved = reserve(3);
        STORE.status.put(pathOf(reserved.ids().get(1)), 404);

        CompleteUploadResult result = service.complete(new CompleteUploadCommand(
            reserved.room(), owner, List.of(reserved.ids().get(2), -1L, reserved.ids().get(1), reserved.ids().get(0))));

        assertThat(result.registered()).extracting(MediaDetail::mediaId)
            .containsExactly(reserved.ids().get(2), reserved.ids().get(0));
        assertThat(result.failed()).extracting(FailedMedia::code)
            .containsExactly("MEDIA_NOT_FOUND", "UPLOAD_NOT_COMPLETED");
        assertThat(statusOf(reserved.ids().get(1))).isEqualTo(UploadStatus.RESERVED);
        assertThat(count("thumbnail_job where media_id in (?, ?)", reserved.ids().get(2), reserved.ids().get(0)))
            .isEqualTo(2);
    }

    @Test
    void 같은_ID가_중복되면_요청_전체를_롤백한다() {
        Reserved reserved = reserve(1);
        Long id = reserved.ids().get(0);

        assertThatThrownBy(() -> service.complete(new CompleteUploadCommand(reserved.room(), owner, List.of(id, id))))
            .isInstanceOf(IllegalUploadStatusException.class);

        assertNothingRegistered(reserved);
    }

    @Test
    void 이미_처리중인_파일이_섞이면_다른_파일도_등록하지_않는다() {
        Reserved reserved = reserve(2);
        StoredFile second = files.findById(reserved.ids().get(1)).orElseThrow();
        second.startProcessing();
        files.save(second);

        assertThatThrownBy(() -> service.complete(command(reserved))).isInstanceOf(IllegalUploadStatusException.class);

        assertThat(statusOf(reserved.ids().get(0))).isEqualTo(UploadStatus.RESERVED);
        assertThat(count("room_events where room_id = ?", reserved.room())).isZero();
        assertThat(count("thumbnail_job where media_id = ?", reserved.ids().get(0))).isZero();
    }

    @Test
    void 다른_사람의_예약이_섞이면_저장소를_호출하기_전에_거절한다() {
        Reserved reserved = reserve(1);
        long other = auth.authenticate("다른사람").userId();
        StoredFile othersFile = files.savePendingUploads(List.of(reserveFile(reserved.room(), other, "other.jpg")))
            .get(0);

        assertThatThrownBy(() -> service.complete(new CompleteUploadCommand(
            reserved.room(), owner, List.of(reserved.ids().get(0), othersFile.getId()))))
            .isInstanceOf(MediaForbiddenException.class);

        assertThat(STORE.calls).hasValue(0);
        assertNothingRegistered(reserved);
    }

    @Test
    void 저장소의_크기가_신고와_다르면_그_파일만_거절한다() {
        Reserved reserved = reserve(1);
        STORE.size.put(pathOf(reserved.ids().get(0)), 2048L);

        CompleteUploadResult result = service.complete(command(reserved));

        assertThat(result.failed()).extracting(FailedMedia::code).containsExactly("FILE_TOO_LARGE");
        assertNothingRegistered(reserved);
    }

    @Test
    void 저장소가_오류를_돌려주면_아무것도_등록하지_않는다() {
        Reserved reserved = reserve(2);
        STORE.status.put(pathOf(reserved.ids().get(1)), 403);

        assertThatThrownBy(() -> service.complete(command(reserved))).isInstanceOf(RuntimeException.class);

        assertNothingRegistered(reserved);
    }

    @Test
    void 순서가_다른_동시_등록은_하나만_성공하고_HEAD는_4개를_넘지_않는다() throws Exception {
        STORE.delayMs = 10;
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

        assertThat(STORE.peak.get()).isLessThanOrEqualTo(4);
        assertThat(count("room_events where room_id = ? and event_type = 'media.created'", reserved.room()))
            .isEqualTo(10);
        assertThat(count("thumbnail_job where media_id in (select id from stored_file where room_id = ?)",
            reserved.room())).isEqualTo(10);
    }

    @Test
    void 저장소가_응답하지_않으면_503용_예외를_던지고_예약은_그대로_둔다() {
        STORE.delayMs = 3_000;
        Reserved reserved = reserve(1);
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> service.complete(command(reserved)))
            .isInstanceOf(UploadVerificationUnavailableException.class);

        assertThat((System.nanoTime() - startedAt) / 1_000_000_000.0).isLessThan(2.5);
        assertNothingRegistered(reserved);
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

    private String pathOf(Long mediaId) {
        return BUCKET_PATH + files.findById(mediaId).orElseThrow().getStorageKey().value();
    }

    private UploadStatus statusOf(Long mediaId) {
        return files.findById(mediaId).orElseThrow().getStatus();
    }

    private long count(String fromAndWhere, Object... args) {
        return jdbc.queryForObject("select count(*) from " + fromAndWhere, Long.class, args);
    }

    private void assertNothingRegistered(Reserved reserved) {
        for (Long id : reserved.ids()) {
            assertThat(statusOf(id)).isEqualTo(UploadStatus.RESERVED);
            assertThat(count("thumbnail_job where media_id = ?", id)).isZero();
        }
        assertThat(count("room_events where room_id = ?", reserved.room())).isZero();
    }

    /** 실제 R2 가 아니라 로컬 루프백에서 HEAD 에 응답한다. 인증은 확인하지 않는다. */
    private static final class LocalHeadServer implements AutoCloseable {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final Map<String, Integer> status = new ConcurrentHashMap<>();
        final Map<String, Long> size = new ConcurrentHashMap<>();
        volatile int delayMs;
        private final HttpServer server;
        private final ExecutorService executor = Executors.newFixedThreadPool(16);

        LocalHeadServer() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 128);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(delayMs);
                    String path = exchange.getRequestURI().getPath();
                    int code = status.getOrDefault(path, 200);
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(size.getOrDefault(path, 1024L)));
                    if (code == 404) {
                        exchange.getResponseHeaders().set("x-amz-error-code", "NoSuchKey");
                    }
                    exchange.sendResponseHeaders(code, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    // 클라이언트가 timeout 으로 먼저 끊은 연결이다.
                } finally {
                    exchange.close();
                    active.decrementAndGet();
                }
            });
            server.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        // 이전 테스트에서 timeout 으로 끊긴 요청이 서버에서 끝날 때까지 기다린다.
        void awaitIdle() {
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> active.get() == 0);
        }

        void reset() {
            calls.set(0);
            peak.set(0);
            status.clear();
            size.clear();
            delayMs = 0;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
