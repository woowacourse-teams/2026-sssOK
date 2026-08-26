package com.sssok.application.mediafolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.mediafolder.exception.InvalidMediaFolderParamException;
import com.sssok.domain.folder.Folder;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 folderIds 지정/생략 분기, movedToRootMediaIds, notFoundMediaIds만 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RemoveMediaFromFoldersServiceTest {

    @Autowired
    RemoveMediaFromFoldersService removeMediaFromFoldersService;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    StoredFileJpaRepository storedFileJpaRepository;

    @Autowired
    FolderMediaJpaRepository folderMediaJpaRepository;

    private void existingMedia(long id) {
        storedFileJpaRepository.save(new StoredFileJpaEntity(id));
    }

    private void linkedToFolder(long folderId, long mediaId) {
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folderId, mediaId));
    }

    @Test
    void 지정한_폴더와의_관계만_끊는다() {
        Folder folderA = createFolderService.create(1L, "맛집");
        Folder folderB = createFolderService.create(1L, "카페");
        existingMedia(1L);
        linkedToFolder(folderA.getId(), 1L);
        linkedToFolder(folderB.getId(), 1L);

        RemoveMediaFromFoldersResult result =
            removeMediaFromFoldersService.remove(1L, List.of(1L), List.of(folderA.getId()));

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.movedToRootMediaIds()).isEmpty(); // 여전히 folderB에 속해 있으므로 루트 아님
        assertThat(result.folders()).extracting(FolderSummary::id).containsExactly(folderA.getId());
    }

    @Test
    void 마지막_폴더에서_빠지면_movedToRootMediaIds에_포함된다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        linkedToFolder(folder.getId(), 1L);

        RemoveMediaFromFoldersResult result =
            removeMediaFromFoldersService.remove(1L, List.of(1L), List.of(folder.getId()));

        assertThat(result.movedToRootMediaIds()).containsExactly(1L);
    }

    @Test
    void folderIds를_생략하면_속한_모든_폴더에서_빠진다() {
        Folder folderA = createFolderService.create(1L, "맛집");
        Folder folderB = createFolderService.create(1L, "카페");
        existingMedia(1L);
        linkedToFolder(folderA.getId(), 1L);
        linkedToFolder(folderB.getId(), 1L);

        RemoveMediaFromFoldersResult result = removeMediaFromFoldersService.remove(1L, List.of(1L), null);

        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.movedToRootMediaIds()).containsExactly(1L);
        assertThat(result.folders()).extracting(FolderSummary::id)
            .containsExactlyInAnyOrder(folderA.getId(), folderB.getId());
    }

    @Test
    void 이미_루트인_미디어는_movedToRootMediaIds에_포함되지_않는다() {
        existingMedia(1L);

        RemoveMediaFromFoldersResult result = removeMediaFromFoldersService.remove(1L, List.of(1L), null);

        assertThat(result.updatedCount()).isZero();
        assertThat(result.movedToRootMediaIds()).isEmpty();
    }

    @Test
    void 존재하지_않는_미디어는_건너뛰고_notFoundMediaIds로_보고한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        existingMedia(1L);
        linkedToFolder(folder.getId(), 1L);

        RemoveMediaFromFoldersResult result =
            removeMediaFromFoldersService.remove(1L, List.of(1L, 999L), List.of(folder.getId()));

        assertThat(result.notFoundMediaIds()).containsExactly(999L);
    }

    @Test
    void 없는_폴더가_있으면_예외() {
        existingMedia(1L);

        assertThatThrownBy(() -> removeMediaFromFoldersService.remove(1L, List.of(1L), List.of(-1L)))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void mediaIds가_비어있으면_예외() {
        assertThatThrownBy(() -> removeMediaFromFoldersService.remove(1L, List.of(), null))
            .isInstanceOf(InvalidMediaFolderParamException.class);
    }
}
