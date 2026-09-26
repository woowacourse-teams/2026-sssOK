package com.sssok.presentation.api.media;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.room.Room;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

// 커밋 뒤 도는 오브젝트 정리를 끈다. 그 스레드가 fileStoragePort 목을 동시에 건드리면 검증이
// 간헐적으로 깨진다. 여기서 확인할 것은 "커밋 시점에 정리 대상이 남았는가"이고,
// 실제로 지우는 부분은 PurgeOrphanObjectsServiceTest 가 맡는다.
@SpringBootTest(properties = "storage.cleanup.auto-purge=false")
class MediaDeleteApiTest extends PostgresContainerSupport {

    @Autowired
    WebApplicationContext context;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    @Autowired
    OrphanObjectRepository orphanObjectRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @MockitoBean
    FileStoragePort fileStoragePort;

    MockMvc mockMvc;
    Long roomId;
    AuthResult host;
    AuthResult uploader;
    AuthResult other;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        host = anonymousAuthService.authenticate("방장");
        uploader = anonymousAuthService.authenticate("가현");
        other = anonymousAuthService.authenticate("의찬");
        Room room = createRoomService.create(host.userId(), "우테코 회식", null, null).room();
        roomId = room.getId();
        joinRoomService.join(roomId, uploader.userId());
        joinRoomService.join(roomId, other.userId());
    }

    @Test
    void 업로더가_단건_삭제하면_R2와_폴더_관계와_DB_행이_모두_사라진다() throws Exception {
        StoredFile file = saveReady(uploader.userId(), true);
        Folder folder = createFolderService.create(roomId, "1일차");
        transactionTemplate.executeWithoutResult(ignored ->
            folderMediaRepository.attachToFolder(folder.getId(), List.of(file.getId())));

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", bearer(uploader)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedMediaId").value(file.getId()));

        assertThat(fileRepository.findById(file.getId())).isEmpty();
        assertThat(folderMediaRepository.findMediaIdsByFolderId(folder.getId())).isEmpty();
        // 응답이 스토리지 왕복을 기다리지 않는다. 커밋 시점에 남는 것은 정리 대기열뿐이다.
        verifyNoInteractions(fileStoragePort);
        assertThat(pendingKeys())
            .contains(file.getStorageKey(), file.getStorageKey().thumbnail());
        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .anyMatch(event -> event.getEventType().equals("media.deleted")
                && event.getPayload().contains(file.getId().toString()));
    }

    @Test
    void 방장은_다른_사람이_올린_미디어를_삭제할_수_있다() throws Exception {
        StoredFile file = saveReady(uploader.userId(), false);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", bearer(host)))
            .andExpect(status().isOk());

        assertThat(fileRepository.findById(file.getId())).isEmpty();
    }

    @Test
    void 다른_참여자는_삭제할_수_없다() throws Exception {
        StoredFile file = saveReady(uploader.userId(), false);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", bearer(other)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MEDIA_FORBIDDEN"));

        assertThat(fileRepository.findById(file.getId())).isPresent();
    }

    @Test
    void 다건_삭제는_없는_ID를_분리하고_나머지를_삭제한다() throws Exception {
        StoredFile first = saveReady(uploader.userId(), false);
        StoredFile second = saveReady(uploader.userId(), false);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media", roomId)
                .header("Authorization", bearer(uploader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mediaIds\":[%d,999999,%d,%d]}"
                    .formatted(first.getId(), second.getId(), first.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(2))
            .andExpect(jsonPath("$.data.deletedMediaIds[0]").value(first.getId()))
            .andExpect(jsonPath("$.data.deletedMediaIds[1]").value(second.getId()))
            .andExpect(jsonPath("$.data.notFoundMediaIds[0]").value(999999));

        assertThat(fileRepository.findAllByIdIn(List.of(first.getId(), second.getId()))).isEmpty();
        assertThat(pendingKeys()).contains(first.getStorageKey(), second.getStorageKey());
    }

    // 아직 썸네일이 붙지 않은 미디어도 유도한 썸네일 키까지 정리 대상에 올린다.
    // 삭제 트랜잭션이 읽은 thumbnailKey 는 null 이지만, 워커가 그 사이 올렸을 수 있다 (#255).
    @Test
    void 썸네일이_없는_미디어도_썸네일_키까지_정리_대상에_올린다() throws Exception {
        StoredFile file = saveReady(uploader.userId(), false);

        mockMvc.perform(delete("/api/v1/rooms/{roomId}/media/{mediaId}", roomId, file.getId())
                .header("Authorization", bearer(uploader)))
            .andExpect(status().isOk());

        assertThat(file.getThumbnailKey()).isNull();
        assertThat(pendingKeys()).contains(file.getStorageKey().thumbnail());
    }

    private List<StorageKey> pendingKeys() {
        return orphanObjectRepository.findStale(Instant.now().plusSeconds(60), 1000).stream()
            .map(OrphanObject::storageKey)
            .toList();
    }

    private StoredFile saveReady(Long uploaderId, boolean withThumbnail) {
        StoredFile file = StoredFile.reserve(roomId, uploaderId, "사진.jpg", "image/jpeg",
            new FileSize(1024), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        if (withThumbnail) {
            file.completeProcessing(ProcessedMedia.ofImage(
                file.getStorageKey().thumbnail(), 1200, 900, null, null));
        } else {
            file.markReady();
        }
        return fileRepository.save(file);
    }

    private String bearer(AuthResult auth) {
        return "Bearer " + auth.accessToken();
    }
}
