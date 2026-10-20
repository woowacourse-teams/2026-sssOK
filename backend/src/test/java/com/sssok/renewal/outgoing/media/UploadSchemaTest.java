package com.sssok.renewal.outgoing.media;

import com.sssok.renewal.outgoing.database.UploadFolderRepository;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.folder.CreateFolderService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadSizePolicy;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@org.springframework.test.context.TestPropertySource(properties =
    "jwt.secret=bG9jYWwtY29udHJhY3QtdGVzdC1rZXktbm90LWZvci1wcm9kdWN0aW9uLTU1Nw==")
@Transactional
class UploadSchemaTest extends PostgresContainerSupport {
    @Autowired UploadFileRepository uploadFileRepository;
    @Autowired UploadFolderRepository uploadFolderRepository;
    @Autowired StoredFileJpaRepository files;
    @Autowired FolderMediaJpaRepository links;
    @Autowired AnonymousAuthService auth;
    @Autowired CreateRoomService rooms;
    @Autowired CreateFolderService folders;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    private Long memberId;
    private Long roomId;

    @BeforeEach
    void prepare() {
        memberId = auth.authenticate("스키마").userId();
        roomId = rooms.create(memberId, "스키마 방", null).room().getId();
    }

    private void addFileToFolders(Long mediaId, List<Long> folderIds) {
        uploadFolderRepository.addFileToFolders(mediaId, folderIds);
    }

    private StoredFile saveFile() {
        return uploadFileRepository.savePendingUploads(List.of(StoredFile.reserve(roomId, memberId, "a.jpg", "image/jpeg",
            new FileSize(1024), Instant.now(), new UploadSizePolicy(2048, 4096)))).getFirst();
    }

    @Test
    void 저장한_도메인에_ID가_있고_엔티티의_관계를_탐색할_수_있다() {
        StoredFile pendingUploadFile = saveFile();
        com.sssok.domain.folder.Folder folder = folders.create(roomId, "연결");
        addFileToFolders(pendingUploadFile.getId(), List.of(folder.getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(pendingUploadFile.getId()).isNotNull();
        com.sssok.infrastructure.persistence.file.StoredFileJpaEntity entity = files.findById(pendingUploadFile.getId()).orElseThrow();
        assertThat(entity.getRoom().getId()).isEqualTo(roomId);
        com.sssok.infrastructure.persistence.folder.FolderMediaJpaEntity link = links.findAll().stream().filter(l -> l.getMediaId().equals(pendingUploadFile.getId())).findFirst().orElseThrow();
        assertThat(link.getMedia().getStorageKey()).isEqualTo(pendingUploadFile.getStorageKey().value());
        assertThat(link.getFolder().getRoom().getId()).isEqualTo(roomId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"room", "media", "folder"})
    void 참조가_있는_부모를_먼저_삭제할_수_없다(String table) {
        StoredFile file = saveFile();
        com.sssok.domain.folder.Folder folder = folders.create(roomId, "연결");
        addFileToFolders(file.getId(), List.of(folder.getId()));
        entityManager.flush();
        String target = table.equals("media") ? "stored_file" : table;
        Long id = table.equals("room") ? roomId : table.equals("media") ? file.getId() : folder.getId();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + target + " WHERE id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"room", "folder", "media"})
    void 없는_부모를_참조할_수_없다(String target) {
        StoredFile file = saveFile();
        com.sssok.domain.folder.Folder folder = folders.create(roomId, "연결");
        entityManager.flush();
        assertThatThrownBy(() -> {
            if (target.equals("room")) {
                jdbc.update("UPDATE stored_file SET room_id = -1 WHERE id = ?", file.getId());
            } else {
                addFileToFolders(target.equals("media") ? -1L : file.getId(),
                    List.of(target.equals("folder") ? -1L : folder.getId()));
            }
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file_size_bytes = 0", "retry_count = -1", "status = 'INVALID'", "media_type = 'PDF'"})
    void 파일의_저장_불변조건을_DB도_검증한다(String assignment) {
        StoredFile file = saveFile();
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update("UPDATE stored_file SET " + assignment + " WHERE id = ?", file.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_스토리지_키는_중복_저장할_수_없다() {
        StoredFile first = saveFile();
        StoredFile second = saveFile();
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update("UPDATE stored_file SET storage_key = ? WHERE id = ?",
            first.getStorageKey().value(), second.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 회원을_삭제해도_미디어는_남는다() {
        StoredFile file = saveFile();
        entityManager.flush();
        jdbc.update("DELETE FROM member WHERE id = ?", memberId);
        assertThat(jdbc.queryForObject("SELECT uploader_id FROM stored_file WHERE id = ?", Long.class, file.getId()))
            .isEqualTo(memberId);
    }

    @Test
    void Flyway로_만든_실제_제약을_확인한다() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN "
            + "('fk_stored_file_room','fk_folder_room','fk_folder_media_folder','fk_folder_media_media') "
            + "AND contype = 'f' AND convalidated", Integer.class)).isEqualTo(4);
    }
}
