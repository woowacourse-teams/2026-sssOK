package com.sssok.application.mediafolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.media.MediaIdsResolver;
import com.sssok.application.media.ResolvedMediaIds;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.domain.folder.Folder;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트. 담기가 PostgreSQL 전용 네이티브 쿼리(ON CONFLICT)를 쓰므로
// H2가 아닌 실제 PostgreSQL로 돌린다. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 미디어 여러 개를 폴더 하나에 담기, 멱등성, notFoundMediaIds, folderId 404만 검증한다.
@PostgresIntegrationTest
@Transactional
@RecordApplicationEvents
class AddMediaToFoldersServiceTest extends PostgresContainerSupport {

    @Autowired
    AddMediaToFoldersService addMediaToFoldersService;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents applicationEvents;

    // 상태 확인과 폴더 연결 사이에 미디어가 FAILED 로 확정되는 경합을 재현하려고 끼워 넣는다.
    @MockitoSpyBean
    MediaIdsResolver mediaIdsResolver;

    // StoredFileJpaEntity는 존재 확인용 최소 매핑(id만)이라 나머지 NOT NULL 컬럼은 직접 채워야 한다.
    private Long existingMedia(long id) {
        return existingMedia(id, "READY");
    }

    private Long existingMedia(long id, String status) {
        return existingMedia(id, status, 1L);
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

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L, 2L), folder.getId());

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.alreadyInCount()).isZero();
        assertThat(result.notFoundMediaIds()).isEmpty();
        assertThat(result.folder().photoCount()).isEqualTo(2);
    }

    @Test
    void 이미_담긴_미디어는_alreadyInCount로_집계되고_오류가_아니다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        addMediaToFoldersService.add(1L, List.of(1L), folder.getId());
        applicationEvents.clear();

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L), folder.getId());

        assertThat(result.updatedCount()).isZero();
        assertThat(result.alreadyInCount()).isEqualTo(1);
        assertThat(applicationEvents.stream(MediaFoldersUpdatedEvent.class)).isEmpty();
    }

    @Test
    void 썸네일이_아직_없는_PROCESSING_미디어도_담을_수_있다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L, "PROCESSING");

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L), folder.getId());

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.notFoundMediaIds()).isEmpty();
        assertThat(result.folder().photoCount()).isEqualTo(1);
    }

    // 목록에서 고를 때는 PROCESSING 이었는데 요청을 처리하는 도중 FAILED 로 확정되는 경우다.
    // 상태를 미리 확인만 해두면 그 사이에 바뀐 미디어가 담겨 updatedCount 는 1인데
    // photoCount 는 0이 된다. 연결하는 쿼리 자체가 상태를 다시 보므로 둘 다 0으로 맞는다.
    @Test
    void 상태_확인_뒤_FAILED_로_바뀐_미디어는_담기지_않고_두_숫자가_어긋나지_않는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L, "PROCESSING");
        doAnswer(invocation -> {
            ResolvedMediaIds resolved = (ResolvedMediaIds) invocation.callRealMethod();
            jdbcTemplate.update("UPDATE stored_file SET status = 'FAILED' WHERE id = 1");
            return resolved;
        }).when(mediaIdsResolver).resolveVisible(1L, List.of(1L));

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L), folder.getId());

        assertThat(result.updatedCount()).isZero();
        assertThat(result.alreadyInCount()).isZero();
        assertThat(result.folder().photoCount()).isZero();
    }

    @Test
    void 존재하지_않는_미디어는_건너뛰고_notFoundMediaIds로_보고한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);

        AddMediaToFoldersResult result = addMediaToFoldersService.add(1L, List.of(1L, 999L), folder.getId());

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.notFoundMediaIds()).containsExactly(999L);
    }

    @Test
    void 없는_폴더면_예외() {
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(1L), -1L))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 다른_방_소속_폴더는_없는_폴더로_취급한다() {
        Folder folder = createFolderService.create(2L, "맛집");
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(1L), folder.getId()))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void mediaIds가_비어있으면_아무것도_담지_않는다() {
        Folder folder = createFolderService.create(1L, "맛집");

        AddMediaToFoldersResult result = addMediaToFoldersService.add(
            1L, List.of(), folder.getId());

        assertThat(result.updatedCount()).isZero();
    }

    @Test
    void folderId가_없으면_예외() {
        existingMedia(1L);

        assertThatThrownBy(() -> addMediaToFoldersService.add(1L, List.of(1L), null))
            .isInstanceOf(InvalidMediaFolderParamException.class);
    }

}
