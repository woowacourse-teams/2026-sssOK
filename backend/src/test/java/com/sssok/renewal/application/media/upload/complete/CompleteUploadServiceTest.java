package com.sssok.renewal.application.media.upload.complete;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.file.exception.IllegalUploadStatusException;
import com.sssok.domain.room.Room;
import com.sssok.infrastructure.realtime.RoomEventJpaEntity;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.application.media.upload.exception.UploadNotAllowedException;
import com.sssok.renewal.domain.media.upload.UploadRejectionReason;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
import com.sssok.support.H2IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;


// Repository + Service 통합 테스트 (H2). 스토리지에 무엇이 올라와 있는지는 목으로 정해두고,
// 그 결과에 따라 등록이 갈리는지를 본다.
@H2IntegrationTest
@TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:complete-upload-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
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
    FileStorage fileStorage;


    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    @Autowired
    ThumbnailJobJpaRepository thumbnailJobRepository;

    private Long hostId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null).room();
        roomId = room.getId();
        given(fileStorage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn("https://storage.example.com/read");
        given(fileStorage.presignPut(any(), anyString(), any(Duration.class))).willReturn("url");
    }

    private StoredFile reserved(Long uploaderId) {
        return fileRepository.save(StoredFile.reserve(roomId, uploaderId, "a.jpg", MIME,
            new FileSize(SIZE), Instant.now(), SIZE_POLICY));
    }

    private void uploadedAs(long size, String mimeType) {
        given(fileStorage.findUploaded(any()))
            .willReturn(Optional.of(new UploadedObject(size, mimeType)));
    }

    @Test
    void 실물이_신고값과_같으면_등록되고_처리중이_된다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);

        CompleteUploadResult result = complete(roomId, hostId, List.of(file.getId()));

        assertThat(result.registered()).hasSize(1);
        assertThat(result.registered().get(0).status()).isEqualTo("PROCESSING");
        assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus())
            .isEqualTo(UploadStatus.PROCESSING);
    }

    @Test
    void 스토리지에_실물이_없으면_등록되지_않는다() {
        StoredFile file = reserved(hostId);
        given(fileStorage.findUploaded(any())).willReturn(Optional.empty());

        CompleteUploadResult result = complete(roomId, hostId, List.of(file.getId()));

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

        CompleteUploadResult result = complete(roomId, hostId, List.of(file.getId()));

        assertThat(result.failed().get(0).code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void 실제_MIME_이_신고값과_다르면_거부한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, "video/mp4");

        CompleteUploadResult result = complete(roomId, hostId, List.of(file.getId()));

        assertThat(result.failed().get(0).code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void 없는_미디어는_실패로_보고하고_나머지는_등록한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);

        CompleteUploadResult result =
            complete(roomId, hostId, List.of(file.getId(), -1L));

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
            complete(roomId, hostId, List.of(mine.getId(), others.getId())))
            .isInstanceOf(MediaForbiddenException.class);
    }

    @Test
    void 등록에_성공하면_이벤트를_발행한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);

        complete(roomId, hostId, List.of(file.getId()));

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L);
        assertThat(events).extracting(RoomEventJpaEntity::getEventType).contains("media.created");
    }

    @Test
    void 등록에_실패하면_이벤트를_발행하지_않는다() {
        StoredFile file = reserved(hostId);
        given(fileStorage.findUploaded(any())).willReturn(Optional.empty());

        complete(roomId, hostId, List.of(file.getId()));

        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .extracting(RoomEventJpaEntity::getEventType)
            .doesNotContain("media.created");
    }

    @Test
    void 미디어_목록이_비어있으면_예외() {
        assertThatThrownBy(() -> complete(roomId, hostId, List.of()))
            .isInstanceOf(InvalidUploadRequestException.class);
    }

    @Test
    void 스토리지_조회는_트랜잭션_밖에서_수행한다() {
        StoredFile file = reserved(hostId);
        given(fileStorage.findUploaded(any())).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager
                .isActualTransactionActive()).isFalse();
            return Optional.of(new UploadedObject(SIZE, MIME));
        });
        complete(roomId, hostId, List.of(file.getId()));
        assertThat(thumbnailJobRepository.findById(file.getId())).isPresent();
    }

    @Test
    void 뒤쪽_스토리지_조회가_실패하면_어떤_파일도_등록하지_않는다() {
        StoredFile first = reserved(hostId);
        StoredFile second = reserved(hostId);
        given(fileStorage.findUploaded(first.getStorageKey())).willReturn(Optional.of(new UploadedObject(SIZE, MIME)));
        given(fileStorage.findUploaded(second.getStorageKey())).willThrow(new IllegalStateException("storage failed"));
        assertThatThrownBy(() -> complete(roomId, hostId, List.of(first.getId(), second.getId())))
            .isInstanceOf(IllegalStateException.class);
        assertNoRegistration(first, second);
    }

    @Test
    void 뒤쪽_상태_전이가_실패하면_앞쪽_파일과_작업도_롤백한다() {
        StoredFile first = reserved(hostId);
        StoredFile second = reserved(hostId);
        second.startProcessing();
        fileRepository.save(second);
        uploadedAs(SIZE, MIME);
        assertThatThrownBy(() -> complete(roomId, hostId, List.of(first.getId(), second.getId())))
            .isInstanceOf(IllegalUploadStatusException.class);
        assertNoRegistration(first);
        assertThat(thumbnailJobRepository.findById(second.getId())).isEmpty();
    }

    @Test
    void 중복_ID는_기존처럼_상태_전이_실패로_전체_롤백한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);
        assertThatThrownBy(() -> complete(roomId, hostId, List.of(file.getId(), file.getId())))
            .isInstanceOf(IllegalUploadStatusException.class);
        assertNoRegistration(file);
    }

    @Test
    void 부분_성공은_요청_순서와_실패_메시지를_유지한다() {
        StoredFile first = reserved(hostId);
        StoredFile absent = reserved(hostId);
        StoredFile last = reserved(hostId);
        uploadedAs(SIZE, MIME);
        given(fileStorage.findUploaded(absent.getStorageKey())).willReturn(Optional.empty());
        CompleteUploadResult result = complete(roomId, hostId,
            List.of(last.getId(), -1L, absent.getId(), first.getId()));
        assertThat(result.registered()).extracting(MediaDetail::mediaId).containsExactly(last.getId(), first.getId());
        assertThat(result.failed()).containsExactly(
            FailedMedia.of(-1L, UploadRejectionReason.MEDIA_NOT_FOUND),
            FailedMedia.of(absent.getId(), UploadRejectionReason.UPLOAD_NOT_COMPLETED));
        assertThat(thumbnailJobRepository.findById(first.getId())).isPresent();
        assertThat(thumbnailJobRepository.findById(last.getId())).isPresent();
        assertThat(thumbnailJobRepository.findById(absent.getId())).isEmpty();
        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .filteredOn(event -> event.getEventType().equals("media.created")).hasSize(2);
    }

    @Test
    void 빈_목록은_권한보다_먼저_권한은_소유권보다_먼저_검증한다() {
        roomId = createRoomService.create(hostId, "방장 전용", "host").room().getId();
        hostId = anonymousAuthService.authenticate("참여자").userId();
        assertThatThrownBy(() -> complete(roomId, hostId, List.of()))
            .isInstanceOf(InvalidUploadRequestException.class).hasMessage("등록할 미디어가 없습니다");
        StoredFile others = reserved(-1L);
        assertThatThrownBy(() -> complete(roomId, hostId, List.of(others.getId())))
            .isInstanceOf(UploadNotAllowedException.class);
        Mockito.verify(fileStorage, Mockito.never()).findUploaded(any());
    }

    @Test
    void 소유권_실패는_스토리지_조회_전에_요청_전체를_막는다() {
        StoredFile mine = reserved(hostId);
        StoredFile others = reserved(-1L);
        assertThatThrownBy(() -> complete(roomId, hostId, List.of(mine.getId(), others.getId())))
            .isInstanceOf(MediaForbiddenException.class);
        Mockito.verify(fileStorage, Mockito.never()).findUploaded(any());
        assertNoRegistration(mine, others);
    }

    @Test
    void 크기와_MIME이_모두_다르면_크기_실패를_먼저_반환한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE - 1, "video/mp4");
        assertThat(complete(roomId, hostId, List.of(file.getId())).failed())
            .containsExactly(FailedMedia.of(file.getId(), UploadRejectionReason.FILE_TOO_LARGE));
    }

    private void assertNoRegistration(StoredFile... files) {
        for (StoredFile file : files) {
            assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.RESERVED);
            assertThat(thumbnailJobRepository.findById(file.getId())).isEmpty();
        }
        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .extracting(RoomEventJpaEntity::getEventType).doesNotContain("media.created");
    }
    private CompleteUploadResult complete(Long roomId, Long uploaderId, List<Long> mediaIds) {
        return completeUploadService.complete(new CompleteUploadCommand(roomId, uploaderId, mediaIds));
    }
}
