package com.sssok.application.medialike;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.MediaLikeRepository;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.sssok.support.UploadRoomFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class MediaLikeSummaryTest extends PostgresContainerSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaLikeRepository likes;

    @Test
    void 통합_조회는_빈목록_비회원_내좋아요_다른회원_좋아요없는사진을_기존조회와_동일하게_처리한다() {
        UploadRoomFixtures.ensure(jdbc, 1L);
        List<Long> ids = List.of(7678101L, 7678102L, 7678103L);
        for (long id : ids) {
            jdbc.update("""
                INSERT INTO stored_file
                (id,room_id,uploader_id,original_file_name,media_type,file_size_bytes,
                 storage_key,status,created_at,updated_at,reserved_at,retry_count)
                VALUES (?,1,1,'test.jpg','JPEG',1024,?,'READY',now(),now(),now(),0)
                """, id, "summary-test-" + id);
        }
        // media_like는 기존 스키마에서 회원 FK 없이 ID를 보관한다.
        jdbc.update("""
            INSERT INTO media_like(media_id,member_id,created_at,updated_at)
            VALUES (7678101,7678201,now(),now()),(7678101,7678202,now(),now()),
                   (7678102,7678202,now(),now())
            """);
        for (long viewer : List.of(7678201L, 7678202L, 7678203L)) {
            var summary = likes.summarize(viewer, ids);
            assertThat(summary.counts()).isEqualTo(likes.countByMediaIds(ids));
            assertThat(summary.likedIds()).isEqualTo(likes.findLikedMediaIds(viewer, ids));
        }
        assertThat(likes.summarize(null, ids).counts()).isEqualTo(likes.countByMediaIds(ids));
        assertThat(likes.summarize(null, ids).likedIds()).isEmpty();
        assertThat(likes.summarize(7678201L, List.of()).counts()).isEmpty();
        assertThat(likes.summarize(7678201L, List.of()).likedIds()).isEmpty();
    }
}
