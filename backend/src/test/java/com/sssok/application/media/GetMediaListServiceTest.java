package com.sssok.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.folder.CreateFolderService;
import com.sssok.application.folder.exception.FolderNotFoundException;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Repository + Service 통합 테스트 (H2). 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가
// 컨트롤러 앞에서 거르므로 여기서는 다루지 않는다.
@SpringBootTest
@ActiveProfiles("test")
class GetMediaListServiceTest {

    private static final Long ROOM_ID = 700L;
    private static final Long OTHER_ROOM_ID = 701L;

    @Autowired
    GetMediaListService getMediaListService;

    @Autowired
    FileRepository fileRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    CreateFolderService createFolderService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long uploaderId;

    // 이 테스트는 롤백되지 않으므로 방 전용 id 를 쓰고 직접 비운다.
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM folder_media");
        jdbcTemplate.update("DELETE FROM stored_file WHERE room_id IN (?, ?)", ROOM_ID, OTHER_ROOM_ID);
        jdbcTemplate.update("DELETE FROM folder WHERE room_id IN (?, ?)", ROOM_ID, OTHER_ROOM_ID);
        uploaderId = memberRepository.save(Member.register(new Nickname("가현"), Instant.now())).getId();
    }

    @Test
    void 방에_올라온_미디어를_반환한다() {
        StoredFile file = save(ROOM_ID, UploadStatus.READY, Instant.now());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).extracting(MediaDetail::mediaId).containsExactly(file.getId());
    }

    @Test
    void 다른_방의_미디어는_섞이지_않는다() {
        StoredFile mine = save(ROOM_ID, UploadStatus.READY, Instant.now());
        save(OTHER_ROOM_ID, UploadStatus.READY, Instant.now());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).extracting(MediaDetail::mediaId).containsExactly(mine.getId());
    }

    @Test
    void 최신순으로_반환한다() {
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        StoredFile oldest = save(ROOM_ID, UploadStatus.READY, base);
        StoredFile newest = save(ROOM_ID, UploadStatus.READY, base.plusSeconds(60));

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).extracting(MediaDetail::mediaId)
            .containsExactly(newest.getId(), oldest.getId());
    }

    // 한 번에 여러 장을 올리면 createdAt 이 전부 같아서, 시각만으로 정렬하면 순서가
    // 호출마다 바뀐다. 나중에 만들어진 것(= 큰 id)이 먼저 와야 한다.
    @Test
    void 같은_시각에_올라온_미디어도_순서가_고정된다() {
        Instant sameMoment = Instant.parse("2026-08-01T00:00:00Z");
        StoredFile first = save(ROOM_ID, UploadStatus.READY, sameMoment);
        StoredFile second = save(ROOM_ID, UploadStatus.READY, sameMoment);
        StoredFile third = save(ROOM_ID, UploadStatus.READY, sameMoment);

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).extracting(MediaDetail::mediaId)
            .containsExactly(third.getId(), second.getId(), first.getId());
    }

    // 발급만 받고 올리지 않았거나 업로드에 실패한 미디어는 스토리지에 실물이 없다.
    // 목록에 내려보내면 클라이언트가 열 수 없는 빈 항목을 그린다.
    @Test
    void 실물이_없는_미디어는_목록에_나오지_않는다() {
        save(ROOM_ID, UploadStatus.RESERVED, Instant.now());
        save(ROOM_ID, UploadStatus.FAILED, Instant.now());
        StoredFile processing = save(ROOM_ID, UploadStatus.PROCESSING, Instant.now());
        StoredFile ready = save(ROOM_ID, UploadStatus.READY, Instant.now());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).extracting(MediaDetail::mediaId)
            .containsExactlyInAnyOrder(processing.getId(), ready.getId());
    }

    @Test
    void 업로더_이름을_채워서_반환한다() {
        save(ROOM_ID, UploadStatus.READY, Instant.now());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).singleElement()
            .extracting(MediaDetail::uploaderName).isEqualTo("가현");
    }

    // 방을 나간 사람이 올린 사진은 방에 그대로 남는다. 이름만 비고 목록에서 빠지면 안 된다.
    @Test
    void 업로더_회원이_사라져도_미디어는_남는다() {
        save(ROOM_ID, UploadStatus.READY, Instant.now());
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", uploaderId);

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).singleElement()
            .extracting(MediaDetail::uploaderName).isNull();
    }

    @Test
    void 어느_폴더에도_없으면_folderIds가_빈_배열이다() {
        save(ROOM_ID, UploadStatus.READY, Instant.now());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).singleElement().extracting(MediaDetail::folderIds)
            .isEqualTo(List.of());
    }

    @Test
    void 담긴_폴더_목록을_채워서_반환한다() {
        StoredFile file = save(ROOM_ID, UploadStatus.READY, Instant.now());
        Folder folder = createFolderService.create(ROOM_ID, "회식");
        attach(folder.getId(), file.getId());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, null);

        assertThat(media).singleElement().extracting(MediaDetail::folderIds)
            .isEqualTo(List.of(folder.getId()));
    }

    @Test
    void folderId를_주면_그_폴더에_담긴_것만_반환한다() {
        StoredFile inFolder = save(ROOM_ID, UploadStatus.READY, Instant.now());
        save(ROOM_ID, UploadStatus.READY, Instant.now());
        Folder folder = createFolderService.create(ROOM_ID, "회식");
        attach(folder.getId(), inFolder.getId());

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, folder.getId());

        assertThat(media).extracting(MediaDetail::mediaId).containsExactly(inFolder.getId());
    }

    @Test
    void 빈_폴더로_필터하면_빈_목록이_나온다() {
        save(ROOM_ID, UploadStatus.READY, Instant.now());
        Folder folder = createFolderService.create(ROOM_ID, "빈폴더");

        List<MediaDetail> media = getMediaListService.list(ROOM_ID, folder.getId());

        assertThat(media).isEmpty();
    }

    @Test
    void 없는_폴더로_필터하면_404() {
        assertThatThrownBy(() -> getMediaListService.list(ROOM_ID, 999_999L))
            .isInstanceOf(FolderNotFoundException.class);
    }

    // 다른 방 폴더 id 로 남의 방 사진을 들여다볼 수 없어야 한다.
    @Test
    void 다른_방의_폴더로_필터하면_404() {
        Folder otherRoomFolder = createFolderService.create(OTHER_ROOM_ID, "남의방");

        assertThatThrownBy(() -> getMediaListService.list(ROOM_ID, otherRoomFolder.getId()))
            .isInstanceOf(FolderNotFoundException.class);
    }

    private StoredFile save(Long roomId, UploadStatus status, Instant createdAt) {
        StoredFile file = StoredFile.reserve(
            roomId, uploaderId, "사진.jpg", "image/jpeg", new FileSize(1024), createdAt);
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

    // 폴더 담기 어댑터는 PostgreSQL 전용 ON CONFLICT 를 써서 H2 로는 못 돈다.
    private void attach(Long folderId, Long mediaId) {
        jdbcTemplate.update("""
            INSERT INTO folder_media (folder_id, media_id, created_at, updated_at)
            VALUES (?, ?, now(), now())
            """, folderId, mediaId);
    }
}
