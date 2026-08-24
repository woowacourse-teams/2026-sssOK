package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.roomstatus.DeletedRoomStatus;
import com.sssok.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 방 설정 변경이 조회-수정-저장으로 이뤄져서, 낙관적 락 없이는 동시 요청이 서로를 덮어쓴다.
@SpringBootTest
class UpdateRoomConcurrencyTest extends PostgresContainerSupport {

    private static final int THREADS = 6;
    private static final int ROUNDS = 10;

    @Autowired
    UpdateRoomService updateRoomService;

    @Autowired
    DeleteRoomService deleteRoomService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    RoomRepository roomRepository;

    @Test
    void 동시에_수정해도_한_번에_하나씩만_반영된다() throws Exception {
        Long hostId = 방장_생성();
        Room room = createRoomService.create(hostId, "우테코 회식").room();
        Long before = roomRepository.findById(room.getId()).orElseThrow().getVersion();

        List<Object> outcomes = 동시에(THREADS, i -> updateRoomService.update(room.getId(), hostId,
            new UpdateRoomCommand("회식 " + i, null, null)));

        long succeeded = outcomes.stream().filter(o -> o instanceof RoomDetail).count();
        Long after = roomRepository.findById(room.getId()).orElseThrow().getVersion();

        // 성공한 수만큼만 버전이 올라야 한다. 덮어쓰기가 일어나면 이 숫자가 어긋난다.
        assertThat(succeeded).isPositive();
        assertThat(after - before).isEqualTo(succeeded);
    }

    @Test
    void 수정과_삭제가_동시에_들어와도_삭제된_방이_되살아나지_않는다() throws Exception {
        Long hostId = 방장_생성();

        for (int round = 0; round < ROUNDS; round++) {
            Room room = createRoomService.create(hostId, "우테코 회식").room();

            동시에(2, i -> {
                if (i == 0) {
                    return updateRoomService.update(room.getId(), hostId,
                        new UpdateRoomCommand("2차 회식", null, null));
                }
                return deleteRoomService.delete(room.getId(), hostId);
            });

            Room reloaded = roomRepository.findById(room.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isSameAs(DeletedRoomStatus.INSTANCE);
            assertThat(reloaded.getDeletedAt()).isNotNull();
        }
    }

    private Long 방장_생성() {
        return anonymousAuthService.authenticate("가현").userId();
    }

    // 모든 스레드를 같은 지점에서 풀어 조회-저장 구간을 겹치게 만든다.
    // 실패한 요청은 예외를 그대로 담아 돌려준다.
    private List<Object> 동시에(int threads, ThrowingFunction work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit((Callable<Object>) () -> {
                    start.await();
                    return work.apply(index);
                }));
            }
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (Exception e) {
                    outcomes.add(e);
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction {
        Object apply(int index);
    }
}