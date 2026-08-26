package com.sssok.application.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.exception.FolderNameTooLongException;
import com.sssok.domain.folder.exception.InvalidFolderNameException;
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

    @Test
    void 이름이_바뀌고_저장된다() {
        Folder folder = createFolderService.create(1L, "맛집");

        Folder renamed = renameFolderService.rename(1L, folder.getId(), "카페");

        assertThat(renamed.getName().value()).isEqualTo("카페");
    }

    @Test
    void 자기_자신과_같은_이름으로_바꾸면_중복으로_보지_않는다() {
        Folder folder = createFolderService.create(1L, "맛집");

        Folder renamed = renameFolderService.rename(1L, folder.getId(), "맛집");

        assertThat(renamed.getName().value()).isEqualTo("맛집");
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

        Folder renamed = renameFolderService.rename(1L, folder.getId(), "카페");

        assertThat(renamed.getName().value()).isEqualTo("카페");
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
