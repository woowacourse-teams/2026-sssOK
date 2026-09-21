package com.sssok.infrastructure.storage;

import com.sssok.domain.file.StorageKey;
import java.util.ArrayList;
import java.util.List;

// DeleteObjects 는 요청 하나에 키 1000 개까지만 받는다. 넘겨받은 키를 그 상한으로 잘라준다.
//
// 어댑터가 S3Client 를 처음 쓸 때 만들어서 바깥에서 찌를 자리가 없다.
// 자르는 규칙만 따로 두면 실제 스토리지 없이도 검증할 수 있다.
final class StorageKeyBatches {

    static final int MAX_KEYS_PER_REQUEST = 1000;

    private StorageKeyBatches() {
    }

    static List<List<StorageKey>> chunk(List<StorageKey> storageKeys) {
        return chunk(storageKeys, MAX_KEYS_PER_REQUEST);
    }

    static List<List<StorageKey>> chunk(List<StorageKey> storageKeys, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("배치 크기는 1 이상이어야 합니다: " + size);
        }
        List<List<StorageKey>> chunks = new ArrayList<>();
        for (int start = 0; start < storageKeys.size(); start += size) {
            chunks.add(List.copyOf(storageKeys.subList(start, Math.min(start + size, storageKeys.size()))));
        }
        return chunks;
    }
}
