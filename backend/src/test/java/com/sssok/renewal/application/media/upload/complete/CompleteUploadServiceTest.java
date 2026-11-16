package com.sssok.renewal.application.media.upload.complete;

import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;

import com.sssok.application.media.MediaDetail;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.room.Room;
import com.sssok.infrastructure.persistence.medialike.MediaLikeJpaEntity;
import com.sssok.infrastructure.persistence.medialike.MediaLikeJpaRepository;
import com.sssok.infrastructure.realtime.RoomEventJpaEntity;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.media.upload.exception.InvalidUploadRequestException;
import com.sssok.renewal.application.media.upload.exception.MediaForbiddenException;
import com.sssok.renewal.application.media.upload.exception.UploadNotAllowedException;
import com.sssok.renewal.application.media.upload.exception.UploadVerificationUnavailableException;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.domain.media.thumbnail.status.ThumbnailJobStatus;
import com.sssok.renewal.domain.media.upload.UploadRejectionReason;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaRepository;
import com.sssok.renewal.outgoing.database.ThumbnailJobRepository;
import com.sssok.renewal.outgoing.storage.FileStorage.UploadedObject;
import com.sssok.renewal.outgoing.storage.FileStorage;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;


// Repository + Service 통합 테스트 (H2). 스토리지에 무엇이 올라와 있는지는 목으로 정해두고,
// 그 결과에 따라 등록이 갈리는지를 본다.
@H2IntegrationTest
@RecordApplicationEvents
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

    @Autowired
    ThumbnailJobRepository thumbnailJobs;

    @Autowired
    ApplicationEvents events;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    MediaRegistrationService registrationService;
    @Autowired
    MediaLikeJpaRepository likes;

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
    void 등록_트랜잭션이_롤백되면_파일과_작업도_롤백한다() {
        StoredFile first = reserved(hostId);
        StoredFile second = reserved(hostId);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            registrationService.register(roomId, List.of(first, second), "가현");
            status.setRollbackOnly();
        });
        assertNoRegistration(first, second);
    }

    @Test
    void 같은_요청의_중복_ID는_첫_등장_순서대로_한번만_등록한다() {
        StoredFile first = reserved(hostId);
        StoredFile second = reserved(hostId);
        uploadedAs(SIZE, MIME);
        CompleteUploadResult result = complete(roomId, hostId,
            List.of(second.getId(), first.getId(), second.getId()));
        assertThat(result.registered()).extracting(MediaDetail::mediaId)
            .containsExactly(second.getId(), first.getId());
        assertThat(result.failed()).isEmpty();
        assertCreatedCount(2);
        assertThat(events.stream(MediaUploadedEvent.class).count()).isEqualTo(1);
        Mockito.verify(fileStorage, Mockito.times(2)).findUploaded(any());
    }

    @Test
    void 처리중_재요청은_HEAD와_작업과_이벤트를_반복하지_않는다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);
        complete(roomId, hostId, List.of(file.getId()));
        ThumbnailJob job = thumbnailJobs.findById(file.getId()).orElseThrow();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        job.claim(now, now.plusSeconds(30));
        job.retry(now.plusSeconds(60), "TEMPORARY_ERROR");
        thumbnailJobs.save(job);
        Mockito.clearInvocations(fileStorage);
        given(fileStorage.findUploaded(any())).willThrow(new UploadVerificationUnavailableException());

        CompleteUploadResult retried = complete(roomId, hostId, List.of(file.getId()));

        assertThat(retried.registered()).extracting(MediaDetail::status).containsExactly("PROCESSING");
        assertThat(retried.failed()).isEmpty();
        Mockito.verify(fileStorage, Mockito.never()).findUploaded(any());
        ThumbnailJob preserved = thumbnailJobs.findById(file.getId()).orElseThrow();
        assertThat(preserved.getStatus().name()).isEqualTo("RETRY_WAIT");
        assertThat(preserved.getAttempts()).isEqualTo(1);
        assertThat(preserved.getAvailableAt()).isEqualTo(now.plusSeconds(60));
        assertCreatedCount(1);
        assertThat(events.stream(MediaUploadedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void 준비완료_재요청은_현재_파생본과_좋아요를_반환한다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);
        complete(roomId, hostId, List.of(file.getId()));
        StoredFile ready = fileRepository.findById(file.getId()).orElseThrow();
        ready.completeProcessing(ProcessedMedia.ofImage(new StorageKey("thumbnail.jpg"),
            new StorageKey("preview.jpg"), 1200, 900, null, null));
        fileRepository.save(ready);
        likes.save(new MediaLikeJpaEntity(null, file.getId(), hostId));
        Mockito.clearInvocations(fileStorage);

        given(fileStorage.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willAnswer(call -> "https://storage.example.com/" + ((StorageKey) call.getArgument(0)).value());
        MediaDetail retried = complete(roomId, hostId, List.of(file.getId())).registered().getFirst();

        assertThat(retried.status()).isEqualTo("READY");
        assertThat(retried.width()).isEqualTo(1200);
        assertThat(retried.thumbnailUrl()).isNotNull();
        assertThat(retried.displayUrl()).contains("preview.jpg");
        assertThat(retried.likeCount()).isEqualTo(1);
        assertThat(retried.likedByMe()).isTrue();
        Mockito.verify(fileStorage, Mockito.never()).findUploaded(any());
        assertCreatedCount(1);
        assertThat(events.stream(MediaUploadedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void 기존_신규_실패가_섞여도_신규만_등록하고_요청_순서를_보존한다() {
        StoredFile existing = reserved(hostId);
        StoredFile fresh = reserved(hostId);
        StoredFile absent = reserved(hostId);
        uploadedAs(SIZE, MIME);
        complete(roomId, hostId, List.of(existing.getId()));
        given(fileStorage.findUploaded(absent.getStorageKey())).willReturn(Optional.empty());
        Mockito.clearInvocations(fileStorage);

        CompleteUploadResult result = complete(roomId, hostId,
            List.of(fresh.getId(), existing.getId(), absent.getId(), -1L));

        assertThat(result.registered()).extracting(MediaDetail::mediaId)
            .containsExactly(fresh.getId(), existing.getId());
        assertThat(result.failed()).containsExactly(
            FailedMedia.of(absent.getId(), UploadRejectionReason.UPLOAD_NOT_COMPLETED),
            FailedMedia.of(-1L, UploadRejectionReason.MEDIA_NOT_FOUND));
        Mockito.verify(fileStorage, Mockito.never()).findUploaded(existing.getStorageKey());
        assertCreatedCount(2);
        assertThat(events.stream(MediaUploadedEvent.class).count()).isEqualTo(2);
        assertThat(thumbnailJobRepository.findAllById(List.of(existing.getId(), fresh.getId(), absent.getId())))
            .hasSize(2);
    }

    @Test
    void 다른_방의_등록완료_파일은_재요청으로_가져올_수_없다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);
        complete(roomId, hostId, List.of(file.getId()));
        Long otherRoom = createRoomService.create(hostId, "다른 방", null).room().getId();
        CompleteUploadResult result = complete(otherRoom, hostId, List.of(file.getId()));
        assertThat(result.registered()).isEmpty();
        assertThat(result.failed()).containsExactly(FailedMedia.of(file.getId(), UploadRejectionReason.MEDIA_NOT_FOUND));
        assertCreatedCount(1);
    }

    @Test
    void 남의_등록완료_파일도_재요청할_수_없다() {
        StoredFile file = reserved(hostId);
        uploadedAs(SIZE, MIME);
        complete(roomId, hostId, List.of(file.getId()));
        Long guest = anonymousAuthService.authenticate("민수").userId();
        assertThatThrownBy(() -> complete(roomId, guest, List.of(file.getId())))
            .isInstanceOf(MediaForbiddenException.class);
        assertCreatedCount(1);
    }

    @Test
    void 삭제된_파일은_재요청으로_복원하지_않는다() {
        StoredFile file = reserved(hostId);
        fileRepository.deleteAllByIdIn(List.of(file.getId()));
        CompleteUploadResult result = complete(roomId, hostId, List.of(file.getId()));
        assertThat(result.registered()).isEmpty();
        assertThat(result.failed()).containsExactly(FailedMedia.of(file.getId(), UploadRejectionReason.MEDIA_NOT_FOUND));
        assertThat(fileRepository.findById(file.getId())).isEmpty();
    }

    @Test
    void FAILED는_성공으로_간주하지_않고_실물을_재검증한다() {
        StoredFile file = reserved(hostId);
        file.failUpload();
        fileRepository.save(file);
        given(fileStorage.findUploaded(any())).willReturn(Optional.empty());
        assertThat(complete(roomId, hostId, List.of(file.getId())).registered()).isEmpty();
        assertThat(fileRepository.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.FAILED);
        uploadedAs(SIZE, MIME);
        assertThat(complete(roomId, hostId, List.of(file.getId())).registered())
            .extracting(MediaDetail::status).containsExactly("PROCESSING");
        assertCreatedCount(1);
    }

    @Test
    void HEAD를_생략한_파일이_잠금_전에_FAILED로_바뀌면_전체_롤백한다() {
        StoredFile first = reserved(hostId);
        StoredFile second = reserved(hostId);
        second.startProcessing();
        StoredFile verified = fileRepository.save(second);
        second.failUpload();
        fileRepository.save(second);

        assertThatThrownBy(() -> registrationService.register(roomId, List.of(first, verified), "가현"))
            .isInstanceOf(UploadVerificationUnavailableException.class);
        assertNoRegistration(first);
        assertThat(fileRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.FAILED);
    }

    private void assertCreatedCount(int count) {
        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .filteredOn(event -> event.getEventType().equals("media.created")).hasSize(count);
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

    @Test
    void 검증이_일시적으로_불가능하면_예약을_유지하고_같은_ID로_재시도할_수_있다() {
        StoredFile first = reserved(hostId);
        StoredFile second = reserved(hostId);
        uploadedAs(SIZE, MIME);
        given(fileStorage.findUploaded(second.getStorageKey()))
            .willThrow(new UploadVerificationUnavailableException());
        List<Long> ids = List.of(first.getId(), second.getId());

        assertThatThrownBy(() -> complete(roomId, hostId, ids))
            .isInstanceOf(UploadVerificationUnavailableException.class);
        assertNoRegistration(first, second);

        willReturn(Optional.of(new UploadedObject(SIZE, MIME)))
            .given(fileStorage).findUploaded(second.getStorageKey());

        CompleteUploadResult retried = complete(roomId, hostId, ids);

        assertThat(retried.registered()).extracting(MediaDetail::mediaId).containsExactlyElementsOf(ids);
        assertThat(retried.failed()).isEmpty();
        assertThat(thumbnailJobRepository.findAllById(ids)).hasSize(2);
        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L))
            .filteredOn(event -> event.getEventType().equals("media.created")).hasSize(2);
    }

    @Test
    void 일괄_등록은_기존_재시도_이력을_보존하고_실패한_작업만_초기화한다() {
        StoredFile fresh = reserved(hostId);
        StoredFile retrying = reserved(hostId);
        StoredFile failed = reserved(hostId);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant nextAttempt = now.plusSeconds(60);
        ThumbnailJob retryJob = new ThumbnailJob(retrying.getId(), ThumbnailKind.IMAGE, now);
        retryJob.claim(now, now.plusSeconds(30));
        retryJob.retry(nextAttempt, "TEMPORARY_ERROR");
        thumbnailJobs.save(retryJob);
        ThumbnailJob failedJob = new ThumbnailJob(failed.getId(), ThumbnailKind.IMAGE, now);
        failedJob.claim(now, now.plusSeconds(30));
        failedJob.complete(ThumbnailJobStatus.from("FAILED"), now, "CONVERSION_FAILED");
        thumbnailJobs.save(failedJob);
        uploadedAs(SIZE, MIME);

        complete(roomId, hostId, List.of(fresh.getId(), retrying.getId(), failed.getId()));

        ThumbnailJob preserved = thumbnailJobs.findById(retrying.getId()).orElseThrow();
        assertThat(preserved.getStatus().name()).isEqualTo("RETRY_WAIT");
        assertThat(preserved.getAttempts()).isEqualTo(1);
        assertThat(preserved.getAvailableAt()).isEqualTo(nextAttempt);
        assertThat(preserved.getErrorCode()).isEqualTo("TEMPORARY_ERROR");
        for (StoredFile file : List.of(fresh, failed)) {
            ThumbnailJob pending = thumbnailJobs.findById(file.getId()).orElseThrow();
            assertThat(pending.getStatus().name()).isEqualTo("PENDING");
            assertThat(pending.getAttempts()).isZero();
            assertThat(pending.getErrorCode()).isNull();
        }
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
