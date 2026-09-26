package com.sssok.application.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.folder.Folder;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import com.sssok.domain.folder.exception.FolderNameTooLongException;
import com.sssok.domain.folder.exception.InvalidFolderNameException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 폴더 자체의 규칙(이름 검증, 중복, 자기 자신 제외)만 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RenameFolderServiceTest {

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    RenameFolderService renameFolderService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    FolderMediaJpaRepository folderMediaJpaRepository;

    @Test
    void 이름이_바뀌고_저장된다() {
        Folder folder = createFolderService.create(1L, "맛집");

        RenameFolderResult renamed = renameFolderService.rename(1L, folder.getId(), "카페");

        assertThat(renamed.folder().getName().value()).isEqualTo("카페");
    }

    @Test
    void 자기_자신과_같은_이름으로_바꾸면_중복으로_보지_않는다() {
        Folder folder = createFolderService.create(1L, "맛집");

        RenameFolderResult renamed = renameFolderService.rename(1L, folder.getId(), "맛집");

        assertThat(renamed.folder().getName().value()).isEqualTo("맛집");
    }

    @Test
    void 다른_폴더가_쓰는_이름으로_바꾸면_예외() {
        createFolderService.create(1L, "카페");
        Folder folder = createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> renameFolderService.rename(1L, folder.getId(), "카페"))
            .isInstanceOf(DuplicateFolderNameException.class);
    }

    @Test
    void 다른_방의_같은_이름_폴더와는_무관하다() {
        createFolderService.create(2L, "카페");
        Folder folder = createFolderService.create(1L, "맛집");

        RenameFolderResult renamed = renameFolderService.rename(1L, folder.getId(), "카페");

        assertThat(renamed.folder().getName().value()).isEqualTo("카페");
    }

    // 응답의 photoCount 를 0으로 고정해 내보내면, 클라이언트가 그 값으로 배지를 갱신했을 때
    // 사진이 든 폴더가 0장으로 보인다.
    @Test
    void 이름을_바꿔도_담긴_사진_수를_그대로_돌려준다() {
        Folder folder = createFolderService.create(1L, "맛집");
        attachVisibleMedia(folder.getId(), 2);

        RenameFolderResult renamed = renameFolderService.rename(1L, folder.getId(), "카페");

        assertThat(renamed.photoCount()).isEqualTo(2);
    }

    // 아직 올라오지 않은 사진은 목록에 없으니 개수에도 잡히면 안 된다.
    @Test
    void 아직_올라오지_않은_사진은_개수에서_빠진다() {
        Folder folder = createFolderService.create(1L, "맛집");
        attachVisibleMedia(folder.getId(), 1);
        attachReservedMedia(folder.getId());

        RenameFolderResult renamed = renameFolderService.rename(1L, folder.getId(), "카페");

        assertThat(renamed.photoCount()).isEqualTo(1);
    }

    private void attachVisibleMedia(Long folderId, int count) {
        for (int i = 0; i < count; i++) {
            StoredFile file = StoredFile.reserve(1L, 1L, "photo.jpg", "image/jpeg",
                FileSize.ofMegabytes(1), Instant.now());
            file.startProcessing();
            attach(folderId, fileRepository.save(file).getId());
        }
    }

    private void attachReservedMedia(Long folderId) {
        StoredFile file = StoredFile.reserve(1L, 1L, "pending.jpg", "image/jpeg",
            FileSize.ofMegabytes(1), Instant.now());
        attach(folderId, fileRepository.save(file).getId());
    }

    // attachToFolder 는 PostgreSQL 전용 ON CONFLICT 를 써서 H2 로는 못 돈다.
    private void attach(Long folderId, Long mediaId) {
        folderMediaJpaRepository.save(new FolderMediaJpaEntity(null, folderId, mediaId));
    }

    @Test
    void 없는_폴더면_예외() {
        assertThatThrownBy(() -> renameFolderService.rename(1L, -1L, "카페"))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 다른_방_소속_폴더면_예외() {
        Folder folder = createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> renameFolderService.rename(2L, folder.getId(), "카페"))
            .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void 이름이_비어있으면_예외() {
        Folder folder = createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> renameFolderService.rename(1L, folder.getId(), " "))
            .isInstanceOf(InvalidFolderNameException.class);
    }

    @Test
    void 이름이_12자를_넘으면_예외() {
        Folder folder = createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> renameFolderService.rename(1L, folder.getId(), "가".repeat(13)))
            .isInstanceOf(FolderNameTooLongException.class);
    }
}
