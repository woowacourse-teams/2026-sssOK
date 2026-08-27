package com.sssok.application.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.application.port.out.EventSubscriberPort;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 등록 트랜잭션이 롤백되면 SSE 도 나가면 안 된다.
// 커밋 전에 보내면 클라이언트는 서버에 없는 사진을 목록에 그린다.
@SpringBootTest
@ActiveProfiles("test")
class MediaEventAfterCommitTest {

    @Autowired
    MediaRegistrar mediaRegistrar;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @MockitoBean
    FileRepository fileRepository;

    @MockitoBean
    RoomPermissionPort roomPermissionPort;

    // 실제 발행 지점을 목으로 잡는다. room_events 행은 롤백되지만 이미 나간 SSE 는 되돌아가지 않으므로,
    // "발행이 호출됐는가" 를 봐야 커밋 전 발행을 잡을 수 있다.
    @MockitoBean
    EventPublisherPort eventPublisherPort;

    @MockitoBean
    EventSubscriberPort eventSubscriberPort;

    private Long roomId;
    private StoredFile file;

    @BeforeEach
    void setUp() {
        Long hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomId = room.getId();
        file = StoredFile.reserve(roomId, hostId, "a.jpg", "image/jpeg",
            new FileSize(1024L), Instant.now());
    }

    // 이 메서드는 스프링 테스트 기본값에 따라 끝에 롤백된다. register 는 그 트랜잭션에 참여하므로
    // 커밋이 일어나지 않고, 따라서 발행도 일어나면 안 된다.
    // 트랜잭션 안에서 직접 발행하면 이 단언이 깨진다.
    @Test
    @Transactional
    void 커밋되지_않으면_이벤트가_나가지_않는다() {
        given(fileRepository.save(any())).willAnswer(call -> call.getArgument(0));

        mediaRegistrar.register(roomId, List.of(file), "가현");

        verify(eventPublisherPort, never()).publish(any(), anyString(), any());
    }

    @Test
    void 저장에_성공하면_커밋된_뒤에_이벤트가_나간다() {
        given(fileRepository.save(any())).willAnswer(call -> call.getArgument(0));

        mediaRegistrar.register(roomId, List.of(file), "가현");

        verify(eventPublisherPort).publish(any(), eq("media.created"), any());
    }

}
