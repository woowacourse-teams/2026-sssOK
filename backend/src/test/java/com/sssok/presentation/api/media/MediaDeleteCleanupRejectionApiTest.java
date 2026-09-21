package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 정리 작업은 썸네일·zip 워커와 공용 풀(applicationTaskExecutor)을 나눠 쓴다.
// 그 풀이 가득 차면 AFTER_COMMIT 에서의 작업 제출이 TaskRejectedException 으로 거부된다.
//
// 그때도 삭제 자체는 이미 커밋됐으므로 응답은 성공이어야 하고, 지우지 못한 키는 회수 배치가
// 집을 수 있도록 대기열에 남아야 한다. 워커 1개 / 큐 0 으로 풀을 막아 그 상황을 강제한다.
@SpringBootTest(properties = {
    "storage.cleanup.auto-purge=true",
    "spring.task.execution.pool.core-size=1",
    "spring.task.execution.pool.max-size=1",
    "spring.task.execution.pool.queue-capacity=0"
})
class MediaDeleteCleanupRejectionApiTest extends PostgresContainerSupport {

    @Autowired
    WebApplicationContext context;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    OrphanObjectRepository orphanObjectRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    MockMvc mockMvc;
    Long roomId;
    AuthResult uploader;

    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch occupied = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AuthResult host = anonymousAuthService.authenticate("방장");
        uploader = anonymousAuthService.authenticate("가현");
        Room room = createRoomService.create(host.userId(), "우테코 회식", null, null).room();
        roomId = room.getId();
        joinRoomService.join(roomId, uploader.userId());
    }

    @AfterEach
    void tearDown() {
        release.countDown();
    }

    @Test
    void 정리_작업_제출이_거부돼도_삭제는_성공하고_대기열에_남는다() throws Exception {
        // 하나뿐인 워커 스레드를 붙잡아 둔다.
        given(fileStoragePort.deleteAll(anyList())).willAnswer(call -> {
            occupied.countDown();
            release.await(10, TimeUnit.SECONDS);
            return List.of();
        });
        StoredFile blocker = saveReady();
        deleteMedia(blocker).andExpect(status().isOk());
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

        // 큐가 0 이라 이번 제출은 거부된다.
        StoredFile rejected = saveReady();

        deleteMedia(rejected).andExpect(status().isOk());

        assertThat(fileRepository.findById(rejected.getId())).isEmpty();
        // 지우지 못했으니 회수 배치가 집을 수 있도록 대기열에 남아야 한다.
        assertThat(pendingKeys()).contains(rejected.getStorageKey());
    }

    private org.springframework.test.web.servlet.ResultActions deleteMedia(StoredFile file)
        throws Exception {
        return mockMvc.perform(
            delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", "Bearer " + uploader.accessToken()));
    }

    private List<StorageKey> pendingKeys() {
        return orphanObjectRepository.findStale(Instant.now().plusSeconds(60), 1000).stream()
            .map(OrphanObject::storageKey)
            .toList();
    }

    private StoredFile saveReady() {
        StoredFile file = StoredFile.reserve(roomId, uploader.userId(), "사진.jpg", "image/jpeg",
            new FileSize(1024), Instant.now());
        file.startProcessing();
        file.markReady();
        return fileRepository.save(file);
    }
}
