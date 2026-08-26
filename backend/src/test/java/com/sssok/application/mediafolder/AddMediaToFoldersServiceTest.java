package com.sssok.application.mediafolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
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
// 먼저 걸러주므로 여기서는 카테시안 곱 담기, 멱등성, notFoundMediaIds, folderIds 404만 검증한다.
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
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes, storage_key, status, created_at)
            VALUES (?, 1, 1, 'test.jpg', 'JPEG', 1024, ?, 'COMPLETED', now())
            """, id, "test-key-" + id);
        return id;
    }

    @Test
    void 미디어_여러개를_폴더_여러개에_카테시안_곱으로_담는다() {
        Folder folderA = createFolderService.create(1L, "맛집");
        Folder folderB = createFolderService.create(1L, "카페");
        existingMedia(1L);
        existingMedia(2L);

        AddMediaToFoldersResult result =
            addMediaToFoldersService.add(1L, List.of(1L, 2L), List.of(folderA.getId(), folderB.getId()));

        assertThat(result.updatedCount()).isEqualTo(4);
        assertThat(result.alreadyInCount()).isZero();
        assertThat(result.notFoundMediaIds()).isEmpty();
        assertThat(result.folders()).extracting(FolderSummary::photoCount).containsExactly(2, 2);
    }

    @Test
    void 이미_담긴_조합은_alreadyInCount로_집계되고_오류가_아니다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        addMediaToFoldersService.add(1L, List.of(1L), List.of(folder.getId()));

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L), List.of(folder.getId()));

        assertThat(result.updatedCount()).isZero();
        assertThat(result.alreadyInCount()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_미디어는_건너뛰고_notFoundMediaIds로_보고한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L, 999L), List.of(folder.getId()));

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.notFoundMediaIds()).containsExactly(999L);
    }

    @Test
    void 없는_폴더가_하나라도_있으면_전체_거부한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(1L), List.of(folder.getId(), -1L)))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 다른_방_소속_폴더는_없는_폴더로_취급한다() {
        Folder folder = createFolderService.create(2L, "맛집");
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(1L), List.of(folder.getId())))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void mediaIds가_비어있으면_예외() {
        Folder folder = createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(), List.of(folder.getId())))
            .isInstanceOf(InvalidMediaFolderParamException.class);
    }

    @Test
    void folderIds가_비어있으면_예외() {
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(1L), List.of()))
            .isInstanceOf(InvalidMediaFolderParamException.class);
    }
}
