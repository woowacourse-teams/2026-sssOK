package com.sssok.domain.file;

import java.time.Instant;

// 주인이 사라진 스토리지 오브젝트 하나. 삭제 트랜잭션이 남긴 정리 대상이다.
//
// attempts/lastAttemptedAt 은 회수 배치가 같은 키만 되풀이해 집어 다른 키를 굶기지 않도록
// 순서를 정하는 데 쓴다.
public record OrphanObject(Long id, StorageKey storageKey, int attempts, Instant lastAttemptedAt) {
}
