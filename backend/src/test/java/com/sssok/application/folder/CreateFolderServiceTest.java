package com.sssok.application.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.exception.FolderNameTooLongException;
import com.sssok.domain.folder.exception.InvalidFolderNameException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2). 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 폴더 자체의 규칙(이름 검증, 중복)만 검증한다.
// 테스트마다 같은 roomId를 재사용하므로 각 테스트가 끝나면 롤백되어야 서로 간섭하지 않는다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreateFolderServiceTest {

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    FolderRepository folderRepository;

    @Test
    void 폴더가_생성되고_저장된다() {
        Folder folder = createFolderService.create(1L, "맛집");

        assertThat(folder.getId()).isNotNull();
        assertThat(folder.getRoomId()).isEqualTo(1L);
        assertThat(folder.getName().value()).isEqualTo("맛집");
        assertThat(folderRepository.findByRoomIdAndName(1L, "맛집")).isPresent();
    }

    @Test
    void 앞뒤_공백은_제거되고_저장된다() {
        Folder folder = createFolderService.create(1L, "  맛집  ");

        assertThat(folder.getName().value()).isEqualTo("맛집");
    }

    @Test
    void 같은_방에_같은_이름의_폴더가_있으면_예외() {
        createFolderService.create(1L, "맛집");

        assertThatThrownBy(() -> createFolderService.create(1L, "맛집"))
            .isInstanceOf(DuplicateFolderNameException.class);
    }

    @Test
    void 다른_방이면_같은_이름이어도_생성된다() {
        createFolderService.create(1L, "맛집");

        Folder folder = createFolderService.create(2L, "맛집");

        assertThat(folder.getRoomId()).isEqualTo(2L);
    }

    @Test
    void 이름이_비어있으면_예외() {
        assertThatThrownBy(() -> createFolderService.create(1L, " "))
            .isInstanceOf(InvalidFolderNameException.class);
    }

    @Test
    void 이름이_12자를_넘으면_예외() {
        assertThatThrownBy(() -> createFolderService.create(1L, "가".repeat(13)))
            .isInstanceOf(FolderNameTooLongException.class);
    }
}
