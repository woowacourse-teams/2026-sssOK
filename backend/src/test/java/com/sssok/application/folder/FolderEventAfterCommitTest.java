package com.sssok.application.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.exception.DuplicateFolderNameException;
import com.sssok.domain.folder.Folder;
import com.sssok.infrastructure.realtime.RoomEventJpaEntity;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sssok.support.H2IntegrationTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 폴더 변경이 커밋된 뒤에만 room_events 에 남는지 확인한다.
// room_events 에 남는다는 건 곧 구독자 브로드캐스트와 Last-Event-ID 재전송 대상이 된다는 뜻이다
@H2IntegrationTest
class FolderEventAfterCommitTest {

    private static final Long ROOM_ID = 348_000L;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    RenameFolderService renameFolderService;

    @Autowired
    DeleteFolderService deleteFolderService;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM folder WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM room_events WHERE room_id = ?", ROOM_ID);
    }

    private List<RoomEventJpaEntity> publishedEvents() {
        return roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(ROOM_ID, 0L);
    }

    @Test
    void 폴더를_생성하면_folder_created가_발행된다() {
        Folder folder = createFolderService.create(ROOM_ID, "맛집");

        List<RoomEventJpaEntity> events = publishedEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("folder.created");
        assertThat(events.get(0).getPayload())
            .contains("\"folderId\":" + folder.getId())
            .contains("\"name\":\"맛집\"")
            .contains("\"createdAt\"");
    }

    @Test
    void 이름을_바꾸면_바뀐_이름이_담긴_folder_renamed가_발행된다() {
        Folder folder = createFolderService.create(ROOM_ID, "맛집");

        renameFolderService.rename(ROOM_ID, folder.getId(), "카페");

        List<RoomEventJpaEntity> events = publishedEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getEventType()).isEqualTo("folder.renamed");
        assertThat(events.get(1).getPayload())
            .contains("\"folderId\":" + folder.getId())
            .contains("\"name\":\"카페\"");
    }

    @Test
    void 폴더를_삭제하면_folder_deleted가_발행된다() {
        Folder folder = createFolderService.create(ROOM_ID, "맛집");

        deleteFolderService.delete(ROOM_ID, folder.getId());

        List<RoomEventJpaEntity> events = publishedEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getEventType()).isEqualTo("folder.deleted");
        assertThat(events.get(1).getPayload())
            .contains("\"roomId\":" + ROOM_ID)
            .contains("\"folderId\":" + folder.getId());
    }

    @Test
    void 중복_이름으로_실패한_생성_요청은_이벤트를_발행하지_않는다() {
        createFolderService.create(ROOM_ID, "맛집");

        assertThatThrownBy(() -> createFolderService.create(ROOM_ID, "맛집"))
            .isInstanceOf(DuplicateFolderNameException.class);

        assertThat(publishedEvents()).hasSize(1);
    }

    @Test
    void 중복_이름으로_실패한_이름변경_요청은_이벤트를_발행하지_않는다() {
        createFolderService.create(ROOM_ID, "카페");
        Folder folder = createFolderService.create(ROOM_ID, "맛집");

        assertThatThrownBy(() -> renameFolderService.rename(ROOM_ID, folder.getId(), "카페"))
            .isInstanceOf(DuplicateFolderNameException.class);

        assertThat(publishedEvents()).hasSize(2);
    }

    // 이 메서드는 테스트 기본값에 따라 끝에 롤백된다. create 가 그 트랜잭션에 참여하므로 커밋이
    // 일어나지 않고, 따라서 발행도 일어나면 안 된다.
    @Test
    @Transactional
    void 커밋되지_않으면_이벤트가_나가지_않는다() {
        createFolderService.create(ROOM_ID, "맛집");

        assertThat(publishedEvents()).isEmpty();
    }
}
