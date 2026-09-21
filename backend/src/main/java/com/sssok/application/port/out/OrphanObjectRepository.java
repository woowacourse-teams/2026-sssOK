package com.sssok.application.port.out;

import com.sssok.domain.file.OrphanObject;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import java.util.List;

// 정리 대기 중인 스토리지 오브젝트 영속화 출력
public interface OrphanObjectRepository {

    // 삭제 트랜잭션 안에서 불린다. 여기까지 커밋되면 오브젝트는 늦어도 회수 배치가 지운다.
    void saveAll(List<StorageKey> storageKeys);

    // 이 시각보다 오래 남아 있고 그 뒤로 시도한 적 없는 것을, 오래 방치된 순서로 준다.
    List<OrphanObject> findStale(Instant retryBefore, int limit);

    void markAttempted(List<Long> ids, Instant attemptedAt);

    // 정리에 성공한 키를 대기열에서 뺀다. 이미 없는 키를 넘겨도 문제없다.
    void deleteAllByStorageKeyIn(List<StorageKey> storageKeys);
}
