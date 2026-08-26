package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.media.exception.InvalidUploadParamException;
import com.sssok.application.media.exception.MediaForbiddenException;
import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.media.exception.UploadNotAllowedException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.RoomPermissionPort;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.UploadAlreadyCompletedException;
import com.sssok.domain.file.exception.UploadRetryExceededException;
import com.sssok.domain.room.Room;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2).
@SpringBootTest
@ActiveProfiles("test")
class ReissueUploadUrlServiceTest {

    private static final long SIZE = 1024L;
    private static final String MIME = "image/jpeg";
    private static final int MAX_RETRY = 5;

    @Autowired
    ReissueUploadUrlService reissueUploadUrlService;

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

    private Long hostId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomId = room.getId();
        given(roomPermissionPort.canUpload(any(), any())).willReturn(true);
        given(fileStoragePort.presignPut(any(), anyString(), any(Duration.class))).willReturn("new-url");
    }

    private StoredFile reserved(Long uploaderId) {
        return fileRepository.save(StoredFile.reserve(roomId, uploaderId, "a.jpg", MIME,
            new FileSize(SIZE), Instant.now()));
    }

    @Test
    void 같은_미디어_ID로_새_URL을_받는다() {
        StoredFile file = reserved(hostId);

        ReissuedUploadUrl result =
            reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null);

        assertThat(result.mediaId()).isEqualTo(file.getId());
        assertThat(result.fileName()).isEqualTo("a.jpg");
        assertThat(result.uploadUrl()).isEqualTo("new-url");
        assertThat(result.headers()).containsEntry("Content-Type", MIME);
    }

    @Test
    void 재시도_횟수가_오르고_한도를_함께_알려준다() {
        StoredFile file = reserved(hostId);

        ReissuedUploadUrl first = reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null);
        ReissuedUploadUrl second = reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null);

        assertThat(first.retryCount()).isEqualTo(1);
        assertThat(second.retryCount()).isEqualTo(2);
        assertThat(second.maxRetryCount()).isEqualTo(MAX_RETRY);
    }

    @Test
    void 정리_배치_기준_시각이_재발급_시각으로_밀린다() {
        StoredFile file = reserved(hostId);
        Instant before = file.getReservedAt();

        reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null);

        // 재시도 중인 파일이 1시간 룰에 걸려 회수되면 안 된다.
        assertThat(fileRepository.findById(file.getId()).orElseThrow().getReservedAt())
            .isAfterOrEqualTo(before);
    }

    @Test
    void 한도를_넘기면_예외() {
        StoredFile file = reserved(hostId);
        for (int i = 0; i < MAX_RETRY; i++) {
            reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null);
        }

        assertThatThrownBy(() -> reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(UploadRetryExceededException.class);
    }

    @Test
    void 이미_등록된_업로드는_재발급할_수_없다() {
        StoredFile file = reserved(hostId);
        file.startProcessing();
        fileRepository.save(file);

        assertThatThrownBy(() -> reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(UploadAlreadyCompletedException.class);
    }

    @Test
    void 업로더_본인이_아니면_예외() {
        Long guestId = anonymousAuthService.authenticate("민수").userId();
        StoredFile file = reserved(guestId);

        // 방장이라도 남의 예약은 재발급하지 못한다.
        assertThatThrownBy(() -> reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(MediaForbiddenException.class);
    }

    @Test
    void 재발급_시점에_업로드_권한을_다시_본다() {
        StoredFile file = reserved(hostId);
        given(roomPermissionPort.canUpload(any(), any())).willReturn(false);

        // 최초 발급 뒤 방장이 권한을 바꿨을 수 있다.
        assertThatThrownBy(() -> reissueUploadUrlService.reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(UploadNotAllowedException.class);
    }

    @Test
    void 없는_미디어면_예외() {
        assertThatThrownBy(() -> reissueUploadUrlService.reissue(roomId, -1L, hostId, null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 다른_방의_미디어면_예외() {
        StoredFile file = reserved(hostId);
        Long otherRoomId = createRoomService.create(hostId, "다른 회식", null, null).room().getId();

        assertThatThrownBy(() ->
            reissueUploadUrlService.reissue(otherRoomId, file.getId(), hostId, null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 바뀐_크기를_반영한다() {
        StoredFile file = reserved(hostId);

        reissueUploadUrlService.reissue(roomId, file.getId(), hostId, 512L);

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getFileSize().bytes())
            .isEqualTo(512L);
    }

    @Test
    void 바뀐_크기가_0이하면_예외() {
        StoredFile file = reserved(hostId);

        assertThatThrownBy(() -> reissueUploadUrlService.reissue(roomId, file.getId(), hostId, 0L))
            .isInstanceOf(InvalidUploadParamException.class);
    }

    @Test
    void 바뀐_크기가_한도를_넘으면_예외() {
        StoredFile file = reserved(hostId);

        assertThatThrownBy(() ->
            reissueUploadUrlService.reissue(roomId, file.getId(), hostId, 11L * 1024 * 1024))
            .isInstanceOf(FileSizeExceededException.class);
    }
}
