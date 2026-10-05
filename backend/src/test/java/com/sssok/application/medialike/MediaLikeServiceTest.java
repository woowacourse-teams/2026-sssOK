package com.sssok.application.medialike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.media.exception.MediaNotFoundException;
import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.domain.file.UploadStatus;
import com.sssok.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트. 좋아요 추가가 PostgreSQL 전용 네이티브 쿼리(ON CONFLICT)를 쓰므로
// H2가 아닌 실제 PostgreSQL로 돌린다. 방 존재/만료/입장 여부는 RoomMembershipInterceptor가
// 먼저 걸러주므로 여기서는 추가·취소의 멱등성, 개수, 이벤트 발행, 미디어 404만 검증한다.
@SpringBootTest
@Transactional
@RecordApplicationEvents
class MediaLikeServiceTest extends PostgresContainerSupport {

    private static final long ROOM_ID = 1L;
    private static final long MEMBER_ID = 100L;
    private static final long OTHER_MEMBER_ID = 101L;

    @Autowired
    LikeMediaService likeMediaService;

    @Autowired
    UnlikeMediaService unlikeMediaService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents applicationEvents;

    @Autowired
    MediaLikeRepository mediaLikeRepository;

    private long existingMedia(long id, String status) {
        return existingMedia(id, status, ROOM_ID);
    }

    private long existingMedia(long id, String status, long roomId) {
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, ?, now(), now(), now(), 0)
            """, id, roomId, "test-key-" + id, status);
        return id;
    }

    private int likeRows(long mediaId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media_like WHERE media_id = ?", Integer.class, mediaId);
    }

    @Test
    void 좋아요를_누르면_liked가_true이고_개수가_늘며_이벤트가_발행된다() {
        long mediaId = existingMedia(1L, "READY");

        MediaLikeResult result = likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID);

        assertThat(result).isEqualTo(new MediaLikeResult(mediaId, true, 1));
        assertThat(applicationEvents.stream(MediaLikeChangedEvent.class))
            .containsExactly(new MediaLikeChangedEvent(ROOM_ID, mediaId));
    }

    @Test
    void 이미_누른_사진에_다시_누르면_개수가_늘지_않고_이벤트도_없다() {
        long mediaId = existingMedia(1L, "READY");
        likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID);
        applicationEvents.clear();

        MediaLikeResult result = likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID);

        assertThat(result).isEqualTo(new MediaLikeResult(mediaId, true, 1));
        assertThat(likeRows(mediaId)).isEqualTo(1);
        assertThat(applicationEvents.stream(MediaLikeChangedEvent.class)).isEmpty();
    }

    @Test
    void 여러_사람이_누르면_전체_개수가_쌓인다() {
        long mediaId = existingMedia(1L, "READY");
        likeMediaService.like(ROOM_ID, mediaId, OTHER_MEMBER_ID);

        MediaLikeResult result = likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID);

        assertThat(result.likeCount()).isEqualTo(2);
    }

    @Test
    void 썸네일이_아직_없는_PROCESSING_미디어에도_누를_수_있다() {
        long mediaId = existingMedia(1L, "PROCESSING");

        assertThat(likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID).liked()).isTrue();
    }

    @Test
    void 취소하면_liked가_false이고_개수가_줄며_이벤트가_발행된다() {
        long mediaId = existingMedia(1L, "READY");
        likeMediaService.like(ROOM_ID, mediaId, OTHER_MEMBER_ID);
        likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID);
        applicationEvents.clear();

        MediaLikeResult result = unlikeMediaService.unlike(ROOM_ID, mediaId, MEMBER_ID);

        assertThat(result).isEqualTo(new MediaLikeResult(mediaId, false, 1));
        assertThat(applicationEvents.stream(MediaLikeChangedEvent.class))
            .containsExactly(new MediaLikeChangedEvent(ROOM_ID, mediaId));
    }

    @Test
    void 누르지_않은_사진을_취소해도_오류가_아니고_이벤트도_없다() {
        long mediaId = existingMedia(1L, "READY");

        MediaLikeResult result = unlikeMediaService.unlike(ROOM_ID, mediaId, MEMBER_ID);

        assertThat(result).isEqualTo(new MediaLikeResult(mediaId, false, 0));
        assertThat(applicationEvents.stream(MediaLikeChangedEvent.class)).isEmpty();
    }

    @Test
    void 없는_미디어면_404() {
        assertThatThrownBy(() -> likeMediaService.like(ROOM_ID, 999L, MEMBER_ID))
            .isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> unlikeMediaService.unlike(ROOM_ID, 999L, MEMBER_ID))
            .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void 다른_방_미디어면_404() {
        long mediaId = existingMedia(1L, "READY", 2L);

        assertThatThrownBy(() -> likeMediaService.like(ROOM_ID, mediaId, MEMBER_ID))
            .isInstanceOf(MediaNotFoundException.class);
        assertThat(likeRows(mediaId)).isZero();
    }

    @Test
    void 실물이_없는_RESERVED_FAILED_미디어면_404() {
        long reserved = existingMedia(1L, "RESERVED");
        long failed = existingMedia(2L, "FAILED");

        assertThatThrownBy(() -> likeMediaService.like(ROOM_ID, reserved, MEMBER_ID))
            .isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> likeMediaService.like(ROOM_ID, failed, MEMBER_ID))
            .isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> unlikeMediaService.unlike(ROOM_ID, failed, MEMBER_ID))
            .isInstanceOf(MediaNotFoundException.class);
    }

    // 대상 확인과 삽입 사이에 미디어가 FAILED 로 확정되는 경합은 삽입 쿼리가 상태를 다시 보는 것으로 막는다.
    // 그 쿼리를 직접 불러, 상태가 맞지 않는 미디어에는 좋아요가 남지 않는지 본다.
    // (서비스에 스파이를 끼워 재현하면 테스트 컨텍스트가 하나 더 떠 공유 컨테이너의 연결 수가 모자란다)
    @Test
    void 삽입_순간_상태가_맞지_않으면_좋아요를_남기지_않는다() {
        long mediaId = existingMedia(1L, "FAILED");

        boolean added = mediaLikeRepository.likeIfStatusIn(mediaId, MEMBER_ID, UploadStatus.visibleStatuses());

        assertThat(added).isFalse();
        assertThat(likeRows(mediaId)).isZero();
    }
}
