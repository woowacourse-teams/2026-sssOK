package com.sssok.presentation.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
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

@SpringBootTest
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
        verify(fileStoragePort).delete(file.getStorageKey());
        verify(fileStoragePort).delete(file.getThumbnailKey());
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
    }

    private StoredFile saveReady(Long uploaderId, boolean withThumbnail) {
        StoredFile file = StoredFile.reserve(roomId, uploaderId, "사진.jpg", "image/jpeg",
            new FileSize(1024), Instant.now());
        file.startProcessing();
        if (withThumbnail) {
            file.completeProcessing(new ProcessedMedia(
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
