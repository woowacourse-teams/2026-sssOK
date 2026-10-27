package com.sssok.application.medialike;

import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.sssok.support.UploadRoomFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;


@PostgresIntegrationTest
class MediaLikeConcurrencyTest extends PostgresContainerSupport {
    @BeforeEach
    void prepareRooms() {
        memberId = members.save(Member.register(
            new Nickname("좋아요회원"), Instant.now())).getId();
        UploadRoomFixtures.ensure(roomFixtureJdbc, 1L);
    }

    @Autowired
    private JdbcTemplate roomFixtureJdbc;


    private static final int THREADS = 8;
    private static final long ROOM_ID = 1L;
    private long memberId;
    private static final AtomicLong MEDIA_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Autowired
    MemberRepository members;

    @Autowired
    LikeMediaService likeMediaService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 같은_회원이_같은_사진에_동시에_여러_번_눌러도_좋아요는_한_건만_남는다() throws Exception {
        long mediaId = existingMedia();

        List<MediaLikeResult> results = 동시에_좋아요(mediaId);

        assertThat(results).hasSize(THREADS);
        assertThat(results).extracting(MediaLikeResult::liked).containsOnly(true);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media_like WHERE media_id = ?", Integer.class, mediaId)).isEqualTo(1);
    }

    private List<MediaLikeResult> 동시에_좋아요(long mediaId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MediaLikeResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit((Callable<MediaLikeResult>) () -> {
                    start.await();
                    return likeMediaService.like(ROOM_ID, mediaId, memberId);
                }));
            }
            start.countDown();

            List<MediaLikeResult> results = new ArrayList<>();
            for (Future<MediaLikeResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private long existingMedia() {
        long mediaId = MEDIA_ID_SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 1, 'test.jpg', 'JPEG', 1024, ?, 'READY', now(), now(), now(), 0)
            """, mediaId, ROOM_ID, "test-key-" + mediaId);
        return mediaId;
    }
}
