package com.sssok.application.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 폴더 삭제와 folder_media 관계 해제만 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeleteFolderServiceTest {

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    DeleteFolderService deleteFolderService;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    FolderMediaRepository folderMediaRepository;

    @Autowired
    FolderMediaJpaRepository folderMediaJpaRepository;

    @Test
    void 폴더가_삭제된다() {
        Folder folder = createFolderService.create(1L, "맛집");

        DeleteFolderResult result = deleteFolderService.delete(1L, folder.getId());

        assertThat(result.deletedFolderId()).isEqualTo(folder.getId());
        assertThat(folderRepository.findById(folder.getId())).isEmpty();
    }

    @Test
    void 담긴_미디어가_없으면_detachedPhotoCount는_0이다() {
        Folder folder = createFolderService.create(1L, "맛집");

        DeleteFolderResult result = deleteFolderService.delete(1L, folder.getId());

        assertThat(result.detachedPhotoCount()).isZero();
    }

    @Test
    void 담겨있던_미디어와의_관계만_끊기고_개수를_반환한다() {
        Folder folder = createFolderService.create(1L, "맛집");
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folder.getId(), 1L));
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folder.getId(), 2L));

        DeleteFolderResult result = deleteFolderService.delete(1L, folder.getId());

        assertThat(result.detachedPhotoCount()).isEqualTo(2);
        assertThat(folderMediaJpaRepository.findAll()).isEmpty();
    }

    @Test
    void 없는_폴더면_예외() {
        assertThatThrownBy(() -> deleteFolderService.delete(1L, -1L))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 다른_방_소속_폴더면_예외() {
        Folder folder = createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> deleteFolderService.delete(2L, folder.getId()))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 삭제한_폴더_이름으로_다시_생성할_수_있다() {
        Folder folder = createFolderService.create(1L, "맛집");
        deleteFolderService.delete(1L, folder.getId());

        Folder recreated = createFolderService.create(1L, "맛집");

        assertThat(recreated.getId()).isNotEqualTo(folder.getId());
    }
}
