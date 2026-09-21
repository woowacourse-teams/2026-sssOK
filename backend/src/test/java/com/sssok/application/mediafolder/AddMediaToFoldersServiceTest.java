package com.sssok.application.mediafolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.application.media.MediaSelection;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.domain.folder.Folder;
import com.sssok.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트. 담기가 PostgreSQL 전용 네이티브 쿼리(ON CONFLICT)를 쓰므로
// H2가 아닌 실제 PostgreSQL로 돌린다. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 미디어 여러 개를 폴더 하나에 담기, 멱등성, notFoundMediaIds, folderId 404만 검증한다.
@SpringBootTest
@Transactional
class AddMediaToFoldersServiceTest extends PostgresContainerSupport {

    @Autowired
    AddMediaToFoldersService addMediaToFoldersService;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // StoredFileJpaEntity는 존재 확인용 최소 매핑(id만)이라 나머지 NOT NULL 컬럼은 직접 채워야 한다.
    private Long existingMedia(long id) {
        return existingMedia(id, "READY");
    }

    private Long existingMedia(long id, String status) {
        return existingMedia(id, status, 1L);
    }

    // 업로더 필터를 보려면 누가 올렸는지가 달라야 한다.
    private Long existingMediaUploadedBy(long id, long uploaderId) {
        return existingMedia(id, "READY", uploaderId);
    }

    private Long existingMedia(long id, String status, long uploaderId) {
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, 1, ?, 'test.jpg', 'JPEG', 1024, ?, ?, now(), now(), now(), 0)
            """, id, uploaderId, "test-key-" + id, status);
        return id;
    }

    @Test
    void 미디어_여러개를_폴더_하나에_담는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        existingMedia(2L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L, 2L)), folder.getId(), null, MediaUploaderFilter.ALL);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.alreadyInCount()).isZero();
        assertThat(result.notFoundMediaIds()).isEmpty();
        assertThat(result.folder().photoCount()).isEqualTo(2);
    }

    @Test
    void 이미_담긴_미디어는_alreadyInCount로_집계되고_오류가_아니다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L)), folder.getId(), null, MediaUploaderFilter.ALL);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L)), folder.getId(), null, MediaUploaderFilter.ALL);

        assertThat(result.updatedCount()).isZero();
        assertThat(result.alreadyInCount()).isEqualTo(1);
    }

    @Test
    void 썸네일이_아직_없는_PROCESSING_미디어도_담을_수_있다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L, "PROCESSING");

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L)), folder.getId(), null, MediaUploaderFilter.ALL);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.notFoundMediaIds()).isEmpty();
        assertThat(result.folder().photoCount()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_미디어는_건너뛰고_notFoundMediaIds로_보고한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L, 999L)), folder.getId(), null, MediaUploaderFilter.ALL);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.notFoundMediaIds()).containsExactly(999L);
    }

    @Test
    void 없는_폴더면_예외() {
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L)), -1L, null, MediaUploaderFilter.ALL))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 다른_방_소속_폴더는_없는_폴더로_취급한다() {
        Folder folder = createFolderService.create(2L, "맛집");
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L)), folder.getId(), null, MediaUploaderFilter.ALL))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void include의_ids가_비어있으면_아무것도_담지_않는다() {
        Folder folder = createFolderService.create(1L, "맛집");

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, MediaSelection.include(List.of()), folder.getId(), null, MediaUploaderFilter.ALL);

        assertThat(result.updatedCount()).isZero();
    }

    @Test
    void exclude는_방_전체에서_지정한_미디어를_제외하고_담는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        existingMedia(2L);
        existingMedia(3L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, MediaSelection.exclude(List.of(2L)), folder.getId(), null, MediaUploaderFilter.ALL);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.notFoundMediaIds()).isEmpty();
        assertThat(result.folder().photoCount()).isEqualTo(2);
    }

    @Test
    void folderId가_없으면_예외() {
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, MediaSelection.include(List.of(1L)), null, null, MediaUploaderFilter.ALL))
            .isInstanceOf(InvalidMediaFolderParamException.class);
    }

    // #275: 대상 폴더 지정 방식은 그대로 두고, 선택되는 미디어에만 업로더 조건을 건다.
    @Test
    void ME는_요청자가_올린_미디어만_담는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMediaUploadedBy(1L, 7L);
        existingMediaUploadedBy(2L, 8L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, MediaSelection.include(List.of(1L, 2L)), folder.getId(), 7L, MediaUploaderFilter.ME);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(mediaIdsIn(folder.getId())).containsExactly(1L);
    }

    @Test
    void OTHERS는_요청자가_올리지_않은_미디어만_담는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMediaUploadedBy(1L, 7L);
        existingMediaUploadedBy(2L, 8L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, MediaSelection.include(List.of(1L, 2L)), folder.getId(), 7L, MediaUploaderFilter.OTHERS);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(mediaIdsIn(folder.getId())).containsExactly(2L);
    }

    @Test
    void uploader를_생략하면_ALL로_담는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMediaUploadedBy(1L, 7L);
        existingMediaUploadedBy(2L, 8L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, MediaSelection.include(List.of(1L, 2L)), folder.getId(), 7L, null);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(mediaIdsIn(folder.getId())).containsExactlyInAnyOrder(1L, 2L);
    }

    // exclude 는 방 전체에서 빼는 방식이라, 업로더 조건과 겹칠 때 범위를 벗어나기 쉽다.
    @Test
    void exclude와_ME를_함께_적용한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMediaUploadedBy(1L, 7L);
        existingMediaUploadedBy(2L, 7L);
        existingMediaUploadedBy(3L, 8L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, MediaSelection.exclude(List.of(1L)), folder.getId(), 7L, MediaUploaderFilter.ME);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(mediaIdsIn(folder.getId())).containsExactly(2L);
    }

    private List<Long> mediaIdsIn(Long folderId) {
        return jdbcTemplate.queryForList(
            "SELECT media_id FROM folder_media WHERE folder_id = ? ORDER BY media_id",
            Long.class, folderId);
    }
}
