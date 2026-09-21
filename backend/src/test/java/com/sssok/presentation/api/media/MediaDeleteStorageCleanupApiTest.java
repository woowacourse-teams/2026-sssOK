package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 커밋 뒤 오브젝트 정리가 실제로 도는지 확인한다. MediaDeleteApiTest 는 이 트리거를 끄고
// "커밋 시점에 무엇이 남는가"만 보므로, AFTER_COMMIT 과 @Async 배선 자체는 여기서만 검증된다.
@SpringBootTest(properties = "storage.cleanup.auto-purge=true")
class MediaDeleteStorageCleanupApiTest extends PostgresContainerSupport {

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AuthResult host = anonymousAuthService.authenticate("방장");
        uploader = anonymousAuthService.authenticate("가현");
        Room room = createRoomService.create(host.userId(), "우테코 회식", null, null).room();
        roomId = room.getId();
        joinRoomService.join(roomId, uploader.userId());
    }

    @Test
    void 커밋_뒤_원본과_썸네일을_한_번에_지우고_대기열을_비운다() throws Exception {
        given(fileStoragePort.deleteAll(anyList())).willReturn(List.of());
        StoredFile file = saveReady();

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", "Bearer " + uploader.accessToken()))
            .andExpect(status().isOk());

        // 파일 수만큼이 아니라 요청 한 번이다.
        verify(fileStoragePort, timeout(5_000))
            .deleteAll(List.of(file.getStorageKey(), file.getStorageKey().thumbnail()));
        waitUntilPurged(file.getStorageKey());
    }

    // 정리가 실패해도 DB 삭제와 media.deleted 는 이미 커밋돼 있어야 하고,
    // 지우지 못한 키는 회수할 수 있도록 대기열에 남아야 한다.
    @Test
    void 정리가_실패해도_삭제는_완료되고_대기열에_남는다() throws Exception {
        StoredFile file = saveReady();
        given(fileStoragePort.deleteAll(anyList()))
            .willReturn(List.of(file.getStorageKey(), file.getStorageKey().thumbnail()));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", "Bearer " + uploader.accessToken()))
            .andExpect(status().isOk());

        verify(fileStoragePort, timeout(5_000)).deleteAll(anyList());
        assertThat(fileRepository.findById(file.getId())).isEmpty();
        assertThat(pendingKeys()).contains(file.getStorageKey());
    }

    // 정리는 응답을 반환한 뒤 다른 스레드에서 돈다. 대기열이 비는 시점은 응답 시점보다 늦다.
    private void waitUntilPurged(StorageKey storageKey) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (!pendingKeys().contains(storageKey)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("정리 대기열에서 사라지지 않았습니다: " + storageKey.value());
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
        file.completeProcessing(ProcessedMedia.ofImage(
            file.getStorageKey().thumbnail(), 1200, 900, null, null));
        return fileRepository.save(file);
    }
}
