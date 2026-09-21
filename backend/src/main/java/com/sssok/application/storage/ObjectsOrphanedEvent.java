package com.sssok.application.storage;

import com.sssok.domain.file.StorageKey;
import java.util.List;

// 주인이 사라진 오브젝트가 생겼다. 커밋 뒤에 정리하라는 신호다.
//
// MediaDeletedEvent 를 재사용하지 않는다. 그쪽은 이미 SSE 페이로드로 그대로 나가고 있어
// (MediaEventListener), 키를 실으면 스토리지 키가 클라이언트에 노출된다.
public record ObjectsOrphanedEvent(List<StorageKey> storageKeys) {
}
