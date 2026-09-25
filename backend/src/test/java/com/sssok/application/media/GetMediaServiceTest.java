package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Repository + Service 통합 테스트 (H2). 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가
// 컨트롤러 앞에서 거른다.
@SpringBootTest
@ActiveProfiles("test")
class GetMediaServiceTest {

    private static final Long OTHER_ROOM_ID = 711L;
    private static final String PRESIGNED = "https://storage.example.com/signed";

    @Autowired
    GetMediaService getMediaService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStoragePort fileStoragePort;

    private Long hostId;
    private Long uploaderId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = memberRepository.save(Member.register(new Nickname("방장"), Instant.now())).getId();
        uploaderId = memberRepository.save(Member.register(new Nickname("가현"), Instant.now())).getId();
        roomId = createRoomService.create(hostId, "우테코 회식", null, null).room().getId();
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any(Duration.class)))
            .willReturn(PRESIGNED);
    }

    @Test
    void 미디어_메타데이터를_반환한다() {
        StoredFile file = save(roomId, UploadStatus.READY);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.mediaId()).isEqualTo(file.getId());
        assertThat(media.fileName()).isEqualTo("사진.jpg");
        assertThat(media.mimeType()).isEqualTo("image/jpeg");
        assertThat(media.size()).isEqualTo(1024);
        assertThat(media.type()).isEqualTo("IMAGE");
        assertThat(media.status()).isEqualTo("READY");
        assertThat(media.uploaderId()).isEqualTo(uploaderId);
        assertThat(media.uploaderName()).isEqualTo("가현");
        assertThat(media.folderIds()).isEmpty();
    }

    // 워커가 EXIF 에서 읽어 채운다. 카메라가 남기지 않았으면 비어 있다.
    @Test
    void 촬영_시각과_위치를_반환한다() {
        Instant takenAt = Instant.parse("2026-08-01T12:30:00Z");
        GeoPoint seoul = new GeoPoint(
            new BigDecimal("37.566500"), new BigDecimal("126.978000"));
        StoredFile file = processed(roomId, takenAt, seoul);

        MediaFullDetail full = getMediaService.get(roomId, file.getId(), uploaderId);

        assertThat(full.takenAt()).isEqualTo(takenAt);
        assertThat(full.location()).isEqualTo(seoul);
    }

    @Test
    void 촬영_정보가_없으면_비어_있다() {
        StoredFile file = save(roomId, UploadStatus.READY);

        MediaFullDetail full = getMediaService.get(roomId, file.getId(), uploaderId);

        assertThat(full.takenAt()).isNull();
        assertThat(full.location()).isNull();
    }

    @Test
    void 올린_본인은_지울_수_있다() {
        StoredFile file = save(roomId, UploadStatus.READY);

        assertThat(getMediaService.get(roomId, file.getId(), uploaderId).canDelete()).isTrue();
    }

    // 방을 관리하는 사람이라 남이 올린 사진도 내릴 수 있어야 한다.
    @Test
    void 방장은_남이_올린_것도_지울_수_있다() {
        StoredFile file = save(roomId, UploadStatus.READY);

        assertThat(getMediaService.get(roomId, file.getId(), hostId).canDelete()).isTrue();
    }

    @Test
    void 남이_올린_사진은_지울_수_없다() {
        StoredFile file = save(roomId, UploadStatus.READY);
        Long other = memberRepository.save(
            Member.register(new Nickname("의찬"), Instant.now())).getId();

        assertThat(getMediaService.get(roomId, file.getId(), other).canDelete()).isFalse();
    }

    // 썸네일은 워커가 붙이기 전까지 없지만, 원본은 PROCESSING 구간에도 그대로 있어 내려준다.
    @Test
    void 워커가_채우는_값은_처리_전까지_비어_있다() {
        StoredFile file = save(roomId, UploadStatus.PROCESSING);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.thumbnailUrl()).isNull();
        assertThat(media.thumbnailUrlExpiresAt()).isNull();
        assertThat(media.width()).isNull();
        assertThat(media.height()).isNull();
        assertThat(media.duration()).isNull();
    }

    @Test
    void 처리_중이어도_원본_주소는_내려준다() {
        StoredFile file = save(roomId, UploadStatus.PROCESSING);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.originalUrl()).isEqualTo(PRESIGNED);
        assertThat(media.originalUrlExpiresAt()).isNotNull();
    }

    @Test
    // 상세 화면은 원본이 아니라 프리뷰를 쓴다. 1600px 이면 모달에서 확대해도 충분하고,
    // 저장 목적의 다운로드는 다운로드 API 가 따로 맡는다 (#291).
    void 처리가_끝나면_썸네일과_프리뷰_주소가_생기고_원본은_나가지_않는다() {
        StoredFile file = processed(roomId, null, null);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.thumbnailUrl()).isEqualTo(PRESIGNED);
        assertThat(media.thumbnailUrlExpiresAt()).isNotNull();
        assertThat(media.previewUrl()).isEqualTo(PRESIGNED);
        assertThat(media.previewUrlExpiresAt()).isNotNull();
        assertThat(media.originalUrl()).isNull();
        assertThat(media.originalUrlExpiresAt()).isNull();
        assertThat(media.width()).isEqualTo(1200);
        assertThat(media.height()).isEqualTo(900);
    }

    // 이 기능이 생기기 전에 올라온 사진에는 프리뷰가 없다. 썸네일로 내려앉히면 400px 짜리가
    // 전체 화면에 늘어나 눈에 띄게 흐리므로, 그때만 원본을 그대로 내준다.
    @Test
    void 프리뷰가_없는_사진은_원본_주소를_내준다() {
        StoredFile file = save(roomId, UploadStatus.PROCESSING);
        file.completeProcessing(ProcessedMedia.ofImage(
            file.getStorageKey().thumbnail("webp"), null, 1200, 900, null, null));
        fileRepository.save(file);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.previewUrl()).isNull();
        assertThat(media.originalUrl()).isEqualTo(PRESIGNED);
    }

    @Test
    void 없는_mediaId면_404() {
        assertThatThrownBy(() -> getMediaService.get(roomId, 999_999L, uploaderId))
            .isInstanceOf(MediaNotFoundException.class);
    }

    // 403 으로 나누면 남의 방에 그 ID 가 있다는 사실이 드러난다.
    @Test
    void 다른_방의_mediaId면_404() {
        StoredFile file = save(OTHER_ROOM_ID, UploadStatus.READY);

        assertThatThrownBy(() -> getMediaService.get(roomId, file.getId(), uploaderId))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UploadStatus.class, names = {"RESERVED", "FAILED"})
    void 실물이_없는_미디어면_404(UploadStatus status) {
        StoredFile file = save(roomId, status);

        assertThatThrownBy(() -> getMediaService.get(roomId, file.getId(), uploaderId))
            .isInstanceOf(MediaNotFoundException.class);
    }

    // 아직 처리 중이어도 실물은 스토리지에 있어 목록·단건에 나오고, 원본 다운로드도 된다.
    @Test
    void PROCESSING_상태도_조회된다() {
        StoredFile file = save(roomId, UploadStatus.PROCESSING);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.status()).isEqualTo("PROCESSING");
    }

    @Test
    void 업로더_회원이_사라져도_조회된다() {
        StoredFile file = save(roomId, UploadStatus.READY);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", uploaderId);

        MediaDetail media = getMediaService.get(roomId, file.getId(), uploaderId).media();

        assertThat(media.uploaderName()).isNull();
    }

    private StoredFile save(Long targetRoomId, UploadStatus status) {
        StoredFile file = StoredFile.reserve(
            targetRoomId, uploaderId, "사진.jpg", "image/jpeg", new FileSize(1024), Instant.now());
        switch (status) {
            case PROCESSING -> file.startProcessing();
            case READY -> {
                file.startProcessing();
                file.markReady();
            }
            case FAILED -> file.failUpload();
            default -> {
            }
        }
        return fileRepository.save(file);
    }

    // 워커가 처리를 마친 상태. 썸네일 키와 크기, 촬영 정보가 채워져 있다.
    private StoredFile processed(Long targetRoomId, Instant takenAt, GeoPoint location) {
        StoredFile file = save(targetRoomId, UploadStatus.PROCESSING);
        file.completeProcessing(ProcessedMedia.ofImage(
            file.getStorageKey().thumbnail("webp"), file.getStorageKey().preview("webp"),
            1200, 900, takenAt, location));
        return fileRepository.save(file);
    }
}
