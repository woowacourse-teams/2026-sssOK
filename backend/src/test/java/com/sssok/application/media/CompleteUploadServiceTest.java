package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FileStoragePort.UploadedObject;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.room.Room;
import com.sssok.infrastructure.realtime.RoomEventJpaEntity;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 스토리지에 무엇이 올라와 있는지는 목으로 정해두고,
// 그 결과에 따라 등록이 갈리는지를 본다.
@SpringBootTest
@ActiveProfiles("test")
class CompleteUploadServiceTest {

    private static final long SIZE = 1024L;
    private static final String MIME = "image/jpeg";

    @Autowired
    CompleteUploadService completeUploadService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @MockitoBean
    RoomPermissionPort roomPermissionPort;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    private Long hostId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomId = room.getId();
        given(roomPermissionPort.canUpload(any(), any())).willReturn(true);
        given(fileStoragePort.presignPut(any(), anyString(), any(Duration.class))).willReturn("url");
    }

    private StoredFile reserved(Long uploaderId) {
        return fileRepository.save(StoredFile.reserve(roomId, uploaderId, "a.jpg", MIME,
            new FileSize(SIZE), Instant.now()));
    }

    private void uploadedAs(long size, String mimeType) {
        given(fileStoragePort.findUploaded(any()))
            .willReturn(Optional.of(new UploadedObject(size, mimeType)));
    }

    @Test
    void 실물이_신고값과_같으면_등록되고_처리중이_된다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);

        CompleteUploadResult result = completeUploadService.complete(roomId, hostId, List.of(file.getId()));

        assertThat(result.registered()).hasSize(1);
        assertThat(result.registered().get(0).status()).isEqualTo("PROCESSING");
        assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void 스토리지에_실물이_없으면_등록되지_않는다() {
        StoredFile file = reserved(hostId);
        given(fileStoragePort.findUploaded(any())).willReturn(Optional.empty());

        CompleteUploadResult result = completeUploadService.complete(roomId, hostId, List.of(file.getId()));

        // 올리지 않고 등록만 하는 위조를 막는다.
        assertThat(result.registered()).isEmpty();
        assertThat(result.failed().get(0).code()).isEqualTo("UPLOAD_NOT_COMPLETED");
        assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.RESERVED);
    }

    @Test
    void 실제_크기가_신고값과_다르면_거부한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE * 100, MIME);

        CompleteUploadResult result = completeUploadService.complete(roomId, hostId, List.of(file.getId()));

        assertThat(result.failed().get(0).code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void 실제_MIME_이_신고값과_다르면_거부한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, "video/mp4");

        CompleteUploadResult result = completeUploadService.complete(roomId, hostId, List.of(file.getId()));

        assertThat(result.failed().get(0).code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void 없는_미디어는_실패로_보고하고_나머지는_등록한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);

        CompleteUploadResult result =
            completeUploadService.complete(roomId, hostId, List.of(file.getId(), -1L));

        assertThat(result.registered()).hasSize(1);
        assertThat(result.failed().get(0).code()).isEqualTo("MEDIA_NOT_FOUND");
    }

    @Test
    void 남이_예약한_미디어가_섞여있으면_요청_전체를_막는다() {
        Long guestId = anonymousAuthService.authenticate("민수").userId();
        StoredFile mine = reserved(hostId);
        StoredFile others = reserved(guestId);
        uploadedAs(SIZE, MIME);

        assertThatThrownBy(() ->
            completeUploadService.complete(roomId, hostId, List.of(mine.getId(), others.getId())))
            .isInstanceOf(MediaForbiddenException.class);
    }

    @Test
    void 등록에_성공하면_이벤트를_발행한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);

        completeUploadService.complete(roomId, hostId, List.of(file.getId()));

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L);
        assertThat(events).extracting(RoomEventJpaEntity::getEventType).contains("media.created");
    }

    @Test
    void 등록에_실패하면_이벤트를_발행하지_않는다() {
        StoredFile file = reserved(hostId);
        given(fileStoragePort.findUploaded(any())).willReturn(Optional.empty());

        completeUploadService.complete(roomId, hostId, List.of(file.getId()));

        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .extracting(RoomEventJpaEntity::getEventType)
            .doesNotContain("media.created");
    }

    @Test
    void 미디어_목록이_비어있으면_예외() {
        assertThatThrownBy(() -> completeUploadService.complete(roomId, hostId, List.of()))
            .isInstanceOf(InvalidUploadParamException.class);
    }
}
