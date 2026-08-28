package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Repository + Service 통합 테스트 (H2). 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가
// 컨트롤러 앞에서 거른다.
@SpringBootTest
@ActiveProfiles("test")
class GetMediaServiceTest {

    private static final Long OTHER_ROOM_ID = 711L;

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

    private Long hostId;
    private Long uploaderId;
    private Long roomId;

    @BeforeEach
    void setUp() {
        hostId = memberRepository.save(Member.register(new Nickname("방장"), Instant.now())).getId();
        uploaderId = memberRepository.save(Member.register(new Nickname("가현"), Instant.now())).getId();
        roomId = createRoomService.create(hostId, "우테코 회식", null, null).room().getId();
    }

    @Test
    void 미디어_메타데이터를_반환한다() {
        StoredFile file = save(roomId, UploadStatus.READY);

        MediaDetail media = getMediaService.get(roomId, file.getId());

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






    // 썸네일 워커가 붙기 전까지는 채울 수 없는 값들이다. 프론트가 null 을 전제로 만들어야 한다.
    @Test
    void 워커가_채우는_값은_아직_비어_있다() {
        StoredFile file = save(roomId, UploadStatus.PROCESSING);

        MediaDetail media = getMediaService.get(roomId, file.getId());

        assertThat(media.thumbnailUrl()).isNull();
        assertThat(media.originalUrl()).isNull();
        assertThat(media.width()).isNull();
        assertThat(media.height()).isNull();
        assertThat(media.duration()).isNull();
    }


    @Test
    void 없는_mediaId면_404() {
        assertThatThrownBy(() -> getMediaService.get(roomId, 999_999L))
            .isInstanceOf(MediaNotFoundException.class);
    }

    // 403 으로 나누면 남의 방에 그 ID 가 있다는 사실이 드러난다.
    @Test
    void 다른_방의_mediaId면_404() {
        StoredFile file = save(OTHER_ROOM_ID, UploadStatus.READY);

        assertThatThrownBy(() -> getMediaService.get(roomId, file.getId()))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UploadStatus.class, names = {"RESERVED", "FAILED"})
    void 실물이_없는_미디어면_404(UploadStatus status) {
        StoredFile file = save(roomId, status);

        assertThatThrownBy(() -> getMediaService.get(roomId, file.getId()))
            .isInstanceOf(MediaNotFoundException.class);
    }

    // 아직 처리 중이어도 실물은 스토리지에 있어 목록·단건에는 나온다. 다운로드만 409 로 막는다.
    @Test
    void PROCESSING_상태도_조회된다() {
        StoredFile file = save(roomId, UploadStatus.PROCESSING);

        MediaDetail media = getMediaService.get(roomId, file.getId());

        assertThat(media.status()).isEqualTo("PROCESSING");
    }

    @Test
    void 업로더_회원이_사라져도_조회된다() {
        StoredFile file = save(roomId, UploadStatus.READY);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", uploaderId);

        MediaDetail media = getMediaService.get(roomId, file.getId());

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

}
