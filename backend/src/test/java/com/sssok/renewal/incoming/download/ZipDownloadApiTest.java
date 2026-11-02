package com.sssok.renewal.incoming.download;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.RoomMembershipService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.outgoing.storage.AbortableOutputStream;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.AcceptanceTest;
import com.sssok.support.PostgresContainerSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// #582 zip 다운로드 API 계약. 구조를 바꾸기 전 코드와 바꾼 뒤 코드가 이 테스트를 똑같이 통과해야 한다.
// 트랜잭션을 걸지 않는다 — 잡 생성이 실제로 커밋돼야 커밋 후 비동기 압축이 돈다.
// 스토리지만 목으로 두고, 원본 읽기·zip 쓰기·서명을 가로채 무엇을 어떤 이름으로 묶고 서명했는지 확인한다.
// 스프링 컨텍스트를 DownloadApiTest 와 공유하도록 같은 설정을 쓴다. 동시 진행 한도(3)·보관 기간(1h)은 기본값이다.
@AcceptanceTest
@SpringBootTest(properties = {
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU4MQ==",
    "download.presigned-get-ttl=5m"
})
class ZipDownloadApiTest extends PostgresContainerSupport {

    private static final Duration RETENTION = Duration.ofHours(1);

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    RoomMembershipService roomMembershipService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStorage fileStorage;

    private MockMvc mockMvc;
    private Long hostId;
    private String hostToken;
    private Long roomId;
    private String guestToken;
    private Long otherRoomId;
    private final Map<String, byte[]> originals = new LinkedHashMap<>();
    private final AtomicReference<ByteArrayOutputStream> uploadedZip = new AtomicReference<>();
    private final AtomicReference<String> uploadedKey = new AtomicReference<>();
    private final AtomicReference<String> workerThread = new AtomicReference<>();
    private final AtomicBoolean aborted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        given(fileStorage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willAnswer(invocation -> signedUrl(invocation.getArgument(0), invocation.getArgument(1)));
        given(fileStorage.openDownloadStream(any())).willAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            return new ByteArrayInputStream(originals.getOrDefault(key.value(), new byte[0]));
        });
        given(fileStorage.openUploadStream(any(), eq("application/zip"))).willAnswer(invocation -> {
            uploadedKey.set(((StorageKey) invocation.getArgument(0)).value());
            workerThread.set(Thread.currentThread().getName());
            return capturingStream();
        });

        AuthResult host = anonymousAuthService.authenticate("가현");
        hostId = host.userId();
        hostToken = "Bearer " + host.accessToken();
        Room room = createRoomService.create(hostId, "제주 여행", null).room();
        roomId = room.getId();

        AuthResult guest = anonymousAuthService.authenticate("민수");
        guestToken = "Bearer " + guest.accessToken();
        roomMembershipService.join(roomId, room.getCode(), guest.userId());

        AuthResult stranger = anonymousAuthService.authenticate("지수");
        otherRoomId = createRoomService.create(stranger.userId(), "다른 방", null).room().getId();
    }

    private static String signedUrl(StorageKey storageKey, String contentDisposition) {
        return "https://storage.test/" + storageKey.value()
            + "?disposition=" + URLEncoder.encode(contentDisposition, StandardCharsets.UTF_8);
    }

    private AbortableOutputStream capturingStream() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        uploadedZip.set(bytes);
        return new AbortableOutputStream() {
            @Override
            public void write(int b) {
                bytes.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                bytes.write(b, off, len);
            }

            @Override
            public void close() {
                closed.set(true);
            }

            @Override
            public void abort() {
                aborted.set(true);
            }
        };
    }

    private Long media(Long roomId, String fileName, byte[] content) {
        StoredFile file = StoredFile.reserve(
            roomId, hostId, fileName, "image/jpeg", new FileSize(content.length), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        file.markReady();
        StoredFile saved = fileRepository.save(file);
        originals.put(saved.getStorageKey().value(), content);
        return saved.getId();
    }

    private ResultActions requestZip(String body, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/zip", roomId)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions requestZip(String body) throws Exception {
        return requestZip(body, hostToken);
    }

    private ResultActions zipStatus(Long jobId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/zip/{jobId}", roomId, jobId)
            .header("Authorization", token));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    private String mediaIdsBody(Long... mediaIds) {
        return "{\"mediaIds\":[%s]}".formatted(
            java.util.Arrays.stream(mediaIds).map(String::valueOf).collect(Collectors.joining(",")));
    }

    private JsonNode awaitStatus(Long jobId, String expected) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode data = data(zipStatus(jobId, hostToken).andExpect(status().isOk()));
            if (expected.equals(data.path("status").asText())) {
                return data;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("5초 안에 " + expected + " 상태가 되지 않았다");
    }

    private Long insertJob(Long requesterId, String status, Instant readyAt, String failureReason) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
            INSERT INTO download_job (room_id, requester_id, status, media_count, total_size_bytes, file_name,
                zip_storage_key, progress, ready_at, failure_reason, created_at, updated_at)
            VALUES (?, ?, ?, 1, 10, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, roomId, requesterId, status, "sssOK_%d.zip".formatted(roomId),
            readyAt == null ? null : "rooms/%d/downloads/inserted.zip".formatted(roomId),
            "READY".equals(status) ? 100 : 0,
            readyAt == null ? null : Timestamp.from(readyAt), failureReason,
            Timestamp.from(now), Timestamp.from(now));
    }

    @Nested
    class 요청 {

        @Test
        void 접수하면_202와_잡_정보를_주고_커밋_후_비동기로_압축해_READY가_된다() throws Exception {
            Long first = media(roomId, "a.jpg", "hello".getBytes());
            Long second = media(roomId, "b.jpg", "world!!".getBytes());

            JsonNode created = data(requestZip(mediaIdsBody(first, second))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.mediaCount").value(2))
                .andExpect(jsonPath("$.data.totalSize").value(12))
                .andExpect(jsonPath("$.data.fileName").value("sssOK_%d.zip".formatted(roomId))));
            Long jobId = created.path("jobId").asLong();

            JsonNode ready = awaitStatus(jobId, "READY");

            assertThat(ready.path("progress").asInt()).isEqualTo(100);
            assertThat(ready.path("mediaCount").asInt()).isEqualTo(2);
            assertThat(uploadedKey.get()).isEqualTo("rooms/%d/downloads/%d.zip".formatted(roomId, jobId));
            assertThat(workerThread.get()).startsWith("media-worker-");
            assertThat(closed).isTrue();
            assertThat(aborted).isFalse();
        }

        @Test
        void zip_에는_원본_바이트가_그대로_담기고_같은_이름은_번호가_붙는다() throws Exception {
            Long first = media(roomId, "a.jpg", "first".getBytes());
            Long second = media(roomId, "a.jpg", "second".getBytes());
            Long third = media(roomId, "제주.jpg", "third".getBytes());

            Long jobId = data(requestZip(mediaIdsBody(first, second, third))).path("jobId").asLong();
            awaitStatus(jobId, "READY");

            Map<String, String> entries = new LinkedHashMap<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(uploadedZip.get().toByteArray()))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    entries.put(entry.getName(), new String(zip.readAllBytes()));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertThat(entries).containsOnly(
                Map.entry("a.jpg", "first"), Map.entry("a (1).jpg", "second"), Map.entry("제주.jpg", "third"));
        }

        @Test
        void 다른_방_없는_ID_는_빼고_남은_대상만_압축_대상_수에_센다() throws Exception {
            Long inRoom = media(roomId, "a.jpg", "a".getBytes());
            Long otherRoom = media(otherRoomId, "b.jpg", "b".getBytes());

            Long jobId = data(requestZip(mediaIdsBody(inRoom, otherRoom, Long.MAX_VALUE))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.mediaCount").value(1))
                .andExpect(jsonPath("$.data.totalSize").value(1))).path("jobId").asLong();
            awaitStatus(jobId, "READY");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "{}",
            "{\"mediaIds\":[1],\"folderId\":1}",
            "{\"mediaIds\":[1],\"uploader\":\"ME\"}"
        })
        void 대상_지정이_mediaIds_folderId_중_하나가_아니면_400(String body) throws Exception {
            requestZip(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.message").value("다운로드 조건이 올바르지 않습니다"));
        }

        @Test
        void 요청_ID가_1000개를_넘으면_400() throws Exception {
            String body = "{\"mediaIds\":[%s]}".formatted(LongStream.rangeClosed(1, 1001)
                .mapToObj(String::valueOf).collect(Collectors.joining(",")));

            requestZip(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
        }

        @Test
        void 남은_대상이_없으면_404_이고_잡을_만들지_않는다() throws Exception {
            Long otherRoom = media(otherRoomId, "b.jpg", "b".getBytes());

            requestZip(mediaIdsBody(otherRoom, Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
            assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM download_job WHERE requester_id = ?", Long.class, hostId)).isZero();
        }

        @Test
        void 요청자의_진행_중_잡이_한도에_도달하면_429() throws Exception {
            Long mediaId = media(roomId, "a.jpg", "a".getBytes());
            insertJob(hostId, "QUEUED", null, null);
            insertJob(hostId, "RUNNING", null, null);
            insertJob(hostId, "RUNNING", null, null);

            requestZip(mediaIdsBody(mediaId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }

        @Test
        void 끝난_잡은_진행_중_한도에_세지_않는다() throws Exception {
            Long mediaId = media(roomId, "a.jpg", "a".getBytes());
            insertJob(hostId, "READY", Instant.now(), null);
            insertJob(hostId, "FAILED", null, "실패");
            insertJob(hostId, "RUNNING", null, null);
            insertJob(hostId, "RUNNING", null, null);

            Long jobId = data(requestZip(mediaIdsBody(mediaId)).andExpect(status().isAccepted())).path("jobId").asLong();
            awaitStatus(jobId, "READY");
        }

        @Test
        void 방에_입장하지_않은_사용자는_403() throws Exception {
            Long mediaId = media(roomId, "a.jpg", "a".getBytes());
            AuthResult outsider = anonymousAuthService.authenticate("외부인");

            requestZip(mediaIdsBody(mediaId), "Bearer " + outsider.accessToken())
                .andExpect(status().isForbidden());
        }

        @Test
        void 인증_없이_요청하면_401() throws Exception {
            mockMvc.perform(post("/api/v1/rooms/{roomId}/downloads/zip", roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mediaIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 압축_진행 {

        @Test
        void 파일마다_진행률이_따로_커밋돼_압축_도중에도_상태_조회에_보인다() throws Exception {
            Long first = media(roomId, "a.jpg", new byte[100]);
            Long second = media(roomId, "b.jpg", new byte[100]);
            Long third = media(roomId, "c.jpg", new byte[100]);
            // 처리 순서와 상관없이 두 번째로 여는 원본에서 멈춘다. 그 사이 첫 파일의 진행률(33%)이 커밋돼 있어야 한다.
            java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
            CountDownLatch release = new CountDownLatch(1);
            org.mockito.BDDMockito.willAnswer(invocation -> {
                if (opened.incrementAndGet() == 2) {
                    release.await(5, TimeUnit.SECONDS);
                }
                return new ByteArrayInputStream(new byte[100]);
            }).given(fileStorage).openDownloadStream(any());

            Long jobId = data(requestZip(mediaIdsBody(first, second, third))).path("jobId").asLong();
            JsonNode running;
            try {
                running = awaitProgress(jobId, 33);
            } finally {
                release.countDown();
            }
            JsonNode ready = awaitStatus(jobId, "READY");

            assertThat(running.path("status").asText()).isEqualTo("RUNNING");
            assertThat(running.path("downloadUrl").isNull()).isTrue();
            assertThat(ready.path("progress").asInt()).isEqualTo(100);
        }

        private JsonNode awaitProgress(Long jobId, int expected) throws Exception {
            for (int i = 0; i < 100; i++) {
                JsonNode data = data(zipStatus(jobId, hostToken));
                if (data.path("progress").asInt() == expected) {
                    return data;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("5초 안에 진행률 " + expected + " 이 보이지 않았다");
        }

        @Test
        void 원본을_읽다_실패하면_업로드를_중단하고_실패_사유와_함께_FAILED가_된다() throws Exception {
            Long first = media(roomId, "a.jpg", "a".getBytes());
            Long second = media(roomId, "b.jpg", "b".getBytes());
            StorageKey secondKey = fileRepository.findById(second).orElseThrow().getStorageKey();
            org.mockito.BDDMockito.willThrow(new IllegalStateException("원본 없음"))
                .given(fileStorage).openDownloadStream(eq(secondKey));

            Long jobId = data(requestZip(mediaIdsBody(first, second))).path("jobId").asLong();
            JsonNode failed = awaitStatus(jobId, "FAILED");

            assertThat(failed.path("failureReason").asText()).isEqualTo("원본 없음");
            assertThat(failed.path("downloadUrl").isNull()).isTrue();
            assertThat(aborted).isTrue();
            assertThat(closed).isFalse();
        }

        @Test
        void 업로드_스트림을_열지_못해도_FAILED가_된다() throws Exception {
            Long mediaId = media(roomId, "a.jpg", "a".getBytes());
            org.mockito.BDDMockito.willThrow(new IllegalStateException("스토리지 연결 실패"))
                .given(fileStorage).openUploadStream(any(), eq("application/zip"));

            Long jobId = data(requestZip(mediaIdsBody(mediaId))).path("jobId").asLong();

            assertThat(awaitStatus(jobId, "FAILED").path("failureReason").asText()).isEqualTo("스토리지 연결 실패");
        }
    }

    @Nested
    class 상태_조회 {

        @Test
        void READY_면_zip_키를_zip_이름으로_남은_보관_시간만큼_새로_서명한다() throws Exception {
            Long mediaId = media(roomId, "a.jpg", "a".getBytes());
            Long jobId = data(requestZip(mediaIdsBody(mediaId))).path("jobId").asLong();
            awaitStatus(jobId, "READY");
            String zipName = "sssOK_%d.zip".formatted(roomId);
            String disposition = "attachment; filename=\"%s\"; filename*=UTF-8''%s".formatted(zipName, zipName);
            StorageKey zipKey = new StorageKey("rooms/%d/downloads/%d.zip".formatted(roomId, jobId));
            Instant readyAt = jdbcTemplate.queryForObject(
                "SELECT ready_at FROM download_job WHERE id = ?", Timestamp.class, jobId).toInstant();

            JsonNode first = data(zipStatus(jobId, hostToken));
            JsonNode second = data(zipStatus(jobId, hostToken));

            assertThat(first.path("downloadUrl").asText()).isEqualTo(signedUrl(zipKey, disposition));
            assertThat(Instant.parse(first.path("expiresAt").asText()))
                .isCloseTo(readyAt.plus(RETENTION), org.assertj.core.api.Assertions.within(1,
                    java.time.temporal.ChronoUnit.MILLIS));
            assertThat(second.path("expiresAt").asText()).isEqualTo(first.path("expiresAt").asText());
            org.mockito.ArgumentCaptor<Duration> ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
            verify(fileStorage, org.mockito.Mockito.atLeast(2))
                .presignGet(eq(zipKey), eq(disposition), eq("application/zip"), ttl.capture());
            assertThat(ttl.getAllValues()).allSatisfy(value ->
                assertThat(value).isPositive().isLessThanOrEqualTo(RETENTION));
        }

        @Test
        void 진행_중이면_URL_없이_상태만_준다() throws Exception {
            Long jobId = insertJob(hostId, "QUEUED", null, null);

            zipStatus(jobId, hostToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.progress").value(0))
                .andExpect(jsonPath("$.data.downloadUrl").isEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isEmpty())
                .andExpect(jsonPath("$.data.failureReason").isEmpty());
            verify(fileStorage, never()).presignGet(any(), anyString(), eq("application/zip"), any());
        }

        @Test
        void 실패한_잡은_실패_사유를_준다() throws Exception {
            Long jobId = insertJob(hostId, "FAILED", null, "원본 없음");

            zipStatus(jobId, hostToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("원본 없음"))
                .andExpect(jsonPath("$.data.downloadUrl").isEmpty());
        }

        @Test
        void 본인이_요청한_잡이_아니면_403() throws Exception {
            Long jobId = insertJob(hostId, "QUEUED", null, null);

            zipStatus(jobId, guestToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOWNLOAD_FORBIDDEN"));
        }

        @Test
        void 없는_잡이면_404() throws Exception {
            zipStatus(Long.MAX_VALUE, hostToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOWNLOAD_NOT_FOUND"));
        }

        @Test
        void READY_후_보관_기간이_지나면_410() throws Exception {
            Long jobId = insertJob(hostId, "READY", Instant.now().minus(RETENTION).minusSeconds(60), null);

            zipStatus(jobId, hostToken)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DOWNLOAD_EXPIRED"));
        }

        // #113: 상태 조회는 경로의 roomId 와 잡의 방을 비교하지 않는다. 결함은 별도 이슈로 다루고 지금 동작을 고정한다.
        @Test
        void 경로의_방이_잡의_방과_달라도_본인_잡이면_조회된다() throws Exception {
            Long jobId = insertJob(hostId, "QUEUED", null, null);
            Long hostOtherRoom = createRoomService.create(hostId, "가현의 다른 방", null).room().getId();

            mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/zip/{jobId}", hostOtherRoom, jobId)
                    .header("Authorization", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(jobId));
        }

        @Test
        void 인증_없이_조회하면_401() throws Exception {
            mockMvc.perform(get("/api/v1/rooms/{roomId}/downloads/zip/{jobId}", roomId, 1L))
                .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void 압축_대상은_요청_시점에_고정된다() throws Exception {
        Long mediaId = media(roomId, "a.jpg", "a".getBytes());
        Long jobId = data(requestZip(mediaIdsBody(mediaId))).path("jobId").asLong();

        assertThat(jdbcTemplate.queryForList(
            "SELECT media_id FROM download_job_media WHERE download_job_id = ?", Long.class, jobId))
            .containsExactly(mediaId);
        awaitStatus(jobId, "READY");
    }
}
