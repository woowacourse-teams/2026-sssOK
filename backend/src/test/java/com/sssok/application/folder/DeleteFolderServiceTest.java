package com.sssok.application.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import java.time.Instant;
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

    @Autowired
    FileRepository fileRepository;

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
        attachMedia(folder.getId(), visibleMedia());
        attachMedia(folder.getId(), visibleMedia());

        DeleteFolderResult result = deleteFolderService.delete(1L, folder.getId());

        assertThat(result.detachedPhotoCount()).isEqualTo(2);
        assertThat(folderMediaJpaRepository.findAll()).isEmpty();
    }

    // detachedPhotoCount 는 "루트로 옮겨진 사진 수"다. 아직 올라오지 않아 목록에 없던 미디어는
    // 사용자가 이 폴더에서 본 적이 없으니 세면 안 된다. 관계는 그래도 끊어야 고아 행이 안 남는다.
    @Test
    void 아직_올라오지_않은_미디어는_세지_않지만_관계는_끊는다() {
        Folder folder = createFolderService.create(1L, "맛집");
        attachMedia(folder.getId(), visibleMedia());
        attachMedia(folder.getId(), reservedMedia());

        DeleteFolderResult result = deleteFolderService.delete(1L, folder.getId());

        assertThat(result.detachedPhotoCount()).isEqualTo(1);
        assertThat(folderMediaJpaRepository.findAll()).isEmpty();
    }

    // attachToFolder 는 PostgreSQL 전용 ON CONFLICT 를 써서 H2 로는 못 돈다.
    private void attachMedia(Long folderId, Long mediaId) {
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folderId, mediaId));
    }

    private Long visibleMedia() {
        StoredFile file = StoredFile.reserve(1L, 1L, "photo.jpg", "image/jpeg",
            FileSize.ofMegabytes(1), Instant.now());
        file.startProcessing();
        return fileRepository.save(file).getId();
    }

    private Long reservedMedia() {
        return fileRepository.save(StoredFile.reserve(1L, 1L, "pending.jpg", "image/jpeg",
            FileSize.ofMegabytes(1), Instant.now())).getId();
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
