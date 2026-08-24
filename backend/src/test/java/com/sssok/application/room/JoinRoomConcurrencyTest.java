package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.Room;
import com.sssok.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 입장 멱등성이 동시 요청에서도 지켜지는지 확인한다.
@SpringBootTest
class JoinRoomConcurrencyTest extends PostgresContainerSupport {

    private static final int THREADS = 8;

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Test
    void 같은_사람이_동시에_여러_번_입장해도_신규_참여는_한_번뿐이다() throws Exception {
        Long hostId = anonymousAuthService.authenticate("가현").userId();
        Long guestId = anonymousAuthService.authenticate("민수").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null).room();

        List<JoinRoomResult> results = 동시에_입장(room.getId(), guestId);

        long newly = results.stream().filter(JoinRoomResult::newlyJoined).count();
        assertThat(results).hasSize(THREADS);
        assertThat(newly).isEqualTo(1);
    }

    @Test
    void 동시_입장에서도_참여_시각과_방장_정보가_모두_같은_값으로_내려간다() throws Exception {
        Long hostId = anonymousAuthService.authenticate("가현").userId();
        Long guestId = anonymousAuthService.authenticate("민수").userId();
        Room room = createRoomService.create(hostId, "우테코 회식", null).room();

        List<JoinRoomResult> results = 동시에_입장(room.getId(), guestId);

        assertThat(results).extracting(JoinRoomResult::joinedAt).containsOnly(results.get(0).joinedAt());
        assertThat(results).extracting(JoinRoomResult::hostId).containsOnly(hostId);
        assertThat(results).extracting(JoinRoomResult::displayName).containsOnly("민수");
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)).isPresent();
    }

    // 같은 지점에서 한꺼번에 풀어 조회-저장 구간을 겹치게 만든다.
    private List<JoinRoomResult> 동시에_입장(Long roomId, Long memberId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        try {
            List<Future<JoinRoomResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit((Callable<JoinRoomResult>) () -> {
                    start.await();
                    return joinRoomService.join(roomId, memberId, null);
                }));
            }
            start.countDown();

            List<JoinRoomResult> results = new ArrayList<>();
            for (Future<JoinRoomResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }
            assertThat(failures.get()).isZero();
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}