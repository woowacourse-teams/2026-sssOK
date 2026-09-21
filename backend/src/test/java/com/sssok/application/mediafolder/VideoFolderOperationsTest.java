package com.sssok.application.mediafolder;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.media.MediaSelection;
import com.sssok.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 영상도 사진과 똑같이 폴더에 담기고 빠지는지 본다.
//
// 담기가 PostgreSQL 전용 네이티브 쿼리(ON CONFLICT)를 쓰므로 H2 가 아닌 실제 PostgreSQL 로 돌린다.
// 나머지 영상 흐름(상세·다운로드·삭제)은 VideoMediaOperationsTest 가 맡는다.
@SpringBootTest
@Transactional
class VideoFolderOperationsTest extends PostgresContainerSupport {

    private static final Long ROOM_ID = 1L;

    @Autowired
    AddMediaToFoldersService addMediaToFoldersService;

    @Autowired
    RemoveMediaFromFoldersService removeMediaFromFoldersService;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 영상을_폴더에_담는다() {
        Long videoId = existingVideo(1L);
        Long folderId = createFolderService.create(ROOM_ID, "회식").getId();

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            ROOM_ID, MediaSelection.include(List.of(videoId)), folderId);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(mediaIdsIn(folderId)).containsExactly(videoId);
    }

    @Test
    void 사진과_영상을_한_번에_담는다() {
        Long photoId = existingPhoto(1L);
        Long videoId = existingVideo(2L);
        Long folderId = createFolderService.create(ROOM_ID, "회식").getId();

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            ROOM_ID, MediaSelection.include(List.of(photoId, videoId)), folderId);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(mediaIdsIn(folderId)).containsExactlyInAnyOrder(photoId, videoId);
    }

    @Test
    void 영상을_폴더에서_뺀다() {
        Long videoId = existingVideo(1L);
        Long folderId = createFolderService.create(ROOM_ID, "회식").getId();
        addMediaToFoldersService.add(ROOM_ID, MediaSelection.include(List.of(videoId)), folderId);

        removeMediaFromFoldersService.remove(
            ROOM_ID, MediaSelection.include(List.of(videoId)), List.of(folderId));

        assertThat(mediaIdsIn(folderId)).isEmpty();
    }

    private Long existingVideo(long id) {
        return existingMedia(id, "회식영상.mp4", "MP4");
    }

    private Long existingPhoto(long id) {
        return existingMedia(id, "회식사진.jpg", "JPEG");
    }

    // StoredFileJpaEntity 는 존재 확인용 최소 매핑이라 나머지 NOT NULL 컬럼은 직접 채운다.
    private Long existingMedia(long id, String fileName, String mediaType) {
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, ?, ?, 2048, ?, 'READY', now(), now(), now(), 0)
            """, id, ROOM_ID, fileName, mediaType, "test-key-" + id);
        return id;
    }

    private List<Long> mediaIdsIn(Long folderId) {
        return jdbcTemplate.queryForList(
            "SELECT media_id FROM folder_media WHERE folder_id = ?", Long.class, folderId);
    }
}
