package com.sssok.renewal.application.media.upload.reissue;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.UploadAlreadyCompletedException;
import com.sssok.domain.file.exception.UploadRetryExceededException;
import com.sssok.domain.room.Room;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.application.media.upload.exception.MediaNotFoundException;
import com.sssok.renewal.application.media.upload.exception.UploadNotAllowedException;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.H2IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;


// Repository + Service 통합 테스트 (H2).
@H2IntegrationTest
@TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:reissue-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
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
    FileStorage fileStorage;


    private Long hostId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null).room();
        roomId = room.getId();
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class))).willReturn("new-url");
    }

    private StoredFile reserved(Long uploaderId) {
        return fileRepository.save(StoredFile.reserve(roomId, uploaderId, "a.jpg", MIME,
            new FileSize(SIZE), Instant.now(), SIZE_POLICY));
    }

    @Test
    void 같은_미디어_ID로_새_URL을_받는다() {
        StoredFile file = reserved(hostId);

        ReissuedUploadUrl result =
            reissue(roomId, file.getId(), hostId, null);

        assertThat(result.mediaId()).isEqualTo(file.getId());
        assertThat(result.fileName()).isEqualTo("a.jpg");
        assertThat(result.uploadUrl()).isEqualTo("new-url");
        assertThat(result.headers()).containsEntry("Content-Type", MIME);
    }

    @Test
    void 재시도_횟수가_오르고_한도를_함께_알려준다() {
        StoredFile file = reserved(hostId);

        ReissuedUploadUrl first = reissue(roomId, file.getId(), hostId, null);
        ReissuedUploadUrl second = reissue(roomId, file.getId(), hostId, null);

        assertThat(first.retryCount()).isEqualTo(1);
        assertThat(second.retryCount()).isEqualTo(2);
        assertThat(second.maxRetryCount()).isEqualTo(MAX_RETRY);
    }

    @Test
    void 정리_배치_기준_시각이_재발급_시각으로_밀린다() {
        StoredFile file = reserved(hostId);
        Instant before = file.getReservedAt();

        reissue(roomId, file.getId(), hostId, null);

        // 재시도 중인 파일이 1시간 룰에 걸려 회수되면 안 된다.
        assertThat(fileRepository.findById(file.getId()).orElseThrow().getReservedAt())
            .isAfterOrEqualTo(before);
    }

    @Test
    void 한도를_넘기면_예외() {
        StoredFile file = reserved(hostId);
        for (int i = 0; i < MAX_RETRY; i++) {
            reissue(roomId, file.getId(), hostId, null);
        }

        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(UploadRetryExceededException.class);
    }

    @Test
    void 이미_등록된_업로드는_재발급할_수_없다() {
        StoredFile file = reserved(hostId);
        file.startProcessing();
        fileRepository.save(file);

        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(UploadAlreadyCompletedException.class);
    }

    @Test
    void 업로더_본인이_아니면_예외() {
        Long guestId = anonymousAuthService.authenticate("민수").userId();
        StoredFile file = reserved(guestId);

        // 방장이라도 남의 예약은 재발급하지 못한다.
        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(MediaForbiddenException.class);
    }

    @Test
    void 재발급_시점에_업로드_권한을_다시_본다() {
        roomId = createRoomService.create(hostId, "방장 전용", "host").room().getId();
        hostId = anonymousAuthService.authenticate("참여자").userId();
        StoredFile file = reserved(hostId);

        // 최초 발급 뒤 방장이 권한을 바꿨을 수 있다.
        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, null))
            .isInstanceOf(UploadNotAllowedException.class);
    }

    @Test
    void 없는_미디어면_예외() {
        assertThatThrownBy(() -> reissue(roomId, -1L, hostId, null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 다른_방의_미디어면_예외() {
        StoredFile file = reserved(hostId);
        Long otherRoomId = createRoomService.create(hostId, "다른 회식", null).room().getId();

        assertThatThrownBy(() ->
            reissue(otherRoomId, file.getId(), hostId, null))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 바뀐_크기를_반영한다() {
        StoredFile file = reserved(hostId);

        reissue(roomId, file.getId(), hostId, 512L);

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getFileSize().bytes())
            .isEqualTo(512L);
    }

    @Test
    void 바뀐_크기가_0이하면_예외() {
        StoredFile file = reserved(hostId);

        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, 0L))
            .isInstanceOf(InvalidUploadRequestException.class);
    }

    @Test
    void 바뀐_크기가_상한과_같으면_반영한다() {
        StoredFile file = reserved(hostId);

        reissue(roomId, file.getId(), hostId, 20L * 1024 * 1024);

        assertThat(fileRepository.findById(file.getId()).orElseThrow().getFileSize().bytes())
            .isEqualTo(20L * 1024 * 1024);
    }

    @Test
    void 바뀐_크기가_한도를_넘으면_예외() {
        StoredFile file = reserved(hostId);

        assertThatThrownBy(() ->
            reissue(roomId, file.getId(), hostId, 20L * 1024 * 1024 + 1))
            .isInstanceOf(FileSizeExceededException.class);
    }

    @Test
    void 미디어_존재와_소속은_소유자와_크기보다_먼저_검증한다() {
        StoredFile file = reserved(hostId);
        Long otherRoomId = createRoomService.create(hostId, "다른 방", null).room().getId();
        assertThatThrownBy(() -> reissue(otherRoomId, file.getId(), -1L, 0L))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 소유자는_방_권한과_크기보다_먼저_검증한다() {
        roomId = createRoomService.create(hostId, "방장 전용", "host").room().getId();
        StoredFile file = reserved(hostId);
        assertThatThrownBy(() -> reissue(roomId, file.getId(), -1L, 0L))
            .isInstanceOf(MediaForbiddenException.class);
    }

    @Test
    void 방_권한은_크기보다_먼저_검증한다() {
        roomId = createRoomService.create(hostId, "방장 전용", "host").room().getId();
        hostId = anonymousAuthService.authenticate("참여자").userId();
        StoredFile file = reserved(hostId);
        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, 0L))
            .isInstanceOf(UploadNotAllowedException.class);
    }

    @Test
    void 크기는_업로드_상태보다_먼저_검증한다() {
        StoredFile file = reserved(hostId);
        file.startProcessing();
        fileRepository.save(file);
        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, 0L))
            .isInstanceOf(InvalidUploadRequestException.class).hasMessage("파일 크기가 올바르지 않습니다");
    }

    @Test
    void READY는_거절하고_FAILED는_상태를_유지하며_재발급한다() {
        StoredFile readyFile = reserved(hostId);
        readyFile.startProcessing();
        readyFile.markReady();
        fileRepository.save(readyFile);
        assertThatThrownBy(() -> reissue(roomId, readyFile.getId(), hostId, null))
            .isInstanceOf(UploadAlreadyCompletedException.class);
        StoredFile failedFile = reserved(hostId);
        failedFile.failUpload();
        fileRepository.save(failedFile);
        assertThat(reissue(roomId, failedFile.getId(), hostId, null).retryCount()).isEqualTo(1);
        assertThat(fileRepository.findById(failedFile.getId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.FAILED);
    }

    @Test
    void 서명_실패는_크기와_횟수와_예약시각을_롤백한다() {
        StoredFile file = reserved(hostId);
        StoredFile before = fileRepository.findById(file.getId()).orElseThrow();
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class)))
            .willThrow(new IllegalStateException("signing failed"));
        assertThatThrownBy(() -> reissue(roomId, file.getId(), hostId, 512L))
            .isInstanceOf(IllegalStateException.class);
        StoredFile after = fileRepository.findById(file.getId()).orElseThrow();
        assertThat(after.getFileSize()).isEqualTo(before.getFileSize());
        assertThat(after.getRetryCount()).isEqualTo(before.getRetryCount());
        assertThat(after.getReservedAt()).isEqualTo(before.getReservedAt());
        assertThat(after.getStorageKey()).isEqualTo(before.getStorageKey());
    }
    private ReissuedUploadUrl reissue(Long roomId, Long mediaId, Long requesterId, Long newSize) {
        return reissueUploadUrlService.reissue(new ReissueUploadUrlCommand(roomId, mediaId, requesterId, newSize));
    }
}
