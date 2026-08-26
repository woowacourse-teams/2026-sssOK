package com.sssok.application.port.out;

import com.sssok.domain.file.StorageKey;

// 오브젝트 스토리지 출력
public interface FileStoragePort {

    // 없는 키를 지워도 성공으로 본다 — 배치가 다시 돌아도 안전해야 한다.
    void delete(StorageKey storageKey);
}
