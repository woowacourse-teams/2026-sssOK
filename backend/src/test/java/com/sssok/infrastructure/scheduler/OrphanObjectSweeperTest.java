package com.sssok.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.OrphanObjectRepository;
import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.StorageKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 삭제 직후의 정리가 유실되면 오브젝트는 주인 없이 남아 계속 요금을 먹는다.
// 이 배치가 유일한 회수 경로라, 실제로 회수되는지 확인해둔다.
@SpringBootTest
@ActiveProfiles("test")
class OrphanObjectSweeperTest {

    @Autowired
    OrphanObjectSweeper orphanObjectSweeper;

    @Autowired
    OrphanObjectRepository orphanObjectRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @Test
    void 정리되지_않고_남은_오브젝트를_다시_지우고_대기열에서_뺀다() {
        StorageKey key = enqueued(Instant.now().minus(30, ChronoUnit.MINUTES));
        given(fileStoragePort.deleteAll(anyList())).willReturn(List.of());

        orphanObjectSweeper.sweep();

        assertThat(pending()).noneMatch(orphan -> orphan.storageKey().equals(key));
    }

    // 방금 삭제된 것은 커밋 직후의 비동기 정리가 아직 도는 중일 수 있다.
    // 배치가 끼어들면 같은 오브젝트를 두 번 지우려 든다.
    @Test
    void 방금_들어온_것은_건드리지_않는다() {
        StorageKey key = enqueued(Instant.now());

        orphanObjectSweeper.sweep();

        assertThat(pending()).anyMatch(orphan -> orphan.storageKey().equals(key));
    }

    // 계속 실패하는 키가 매 회차 맨 앞에 남으면 뒤에 쌓인 것들이 영영 처리되지 않는다.
    @Test
    void 지우지_못하면_시도_횟수를_올린_채_대기열에_남긴다() {
        StorageKey key = enqueued(Instant.now().minus(30, ChronoUnit.MINUTES));
        given(fileStoragePort.deleteAll(anyList())).willReturn(List.of(key));

        orphanObjectSweeper.sweep();

        assertThat(pending())
            .filteredOn(orphan -> orphan.storageKey().equals(key))
            .singleElement()
            .satisfies(orphan -> {
                assertThat(orphan.attempts()).isEqualTo(1);
                assertThat(orphan.lastAttemptedAt()).isNotNull();
            });
    }

    private StorageKey enqueued(Instant createdAt) {
        StorageKey key = new StorageKey("rooms/930/%d.jpg".formatted(System.nanoTime()));
        orphanObjectRepository.saveAll(List.of(key));
        // BaseEntity 가 @PrePersist 로 지금 시각을 넣기 때문에, 오래된 행은 직접 만들어야 한다.
        jdbcTemplate.update("UPDATE orphan_object SET created_at = ? WHERE storage_key = ?",
            Timestamp.from(createdAt), key.value());
        return key;
    }

    private List<OrphanObject> pending() {
        return orphanObjectRepository.findStale(Instant.now().plusSeconds(60), 1000);
    }
}
