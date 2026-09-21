package com.sssok.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.domain.file.StorageKey;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StorageKeyBatchesTest {

    // DeleteObjects 는 요청당 1000 키가 상한이다. 넘겨 보내면 요청 자체가 거부된다.
    @Test
    void 키가_1000개를_넘으면_나눈다() {
        List<List<StorageKey>> chunks = StorageKeyBatches.chunk(keys(2500));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(1000);
        assertThat(chunks.get(1)).hasSize(1000);
        assertThat(chunks.get(2)).hasSize(500);
    }

    // 미디어 500 장(원본 + 썸네일 = 1000 키)이 요청 한 번으로 끝나야 한다는 것이 이 작업의 목표다.
    @Test
    void 상한에_딱_맞으면_한_번에_보낸다() {
        assertThat(StorageKeyBatches.chunk(keys(1000))).hasSize(1);
    }

    @Test
    void 비어_있으면_보낼_것이_없다() {
        assertThat(StorageKeyBatches.chunk(List.of())).isEmpty();
    }

    @Test
    void 나눈_뒤에도_키의_순서와_내용이_보존된다() {
        List<StorageKey> keys = keys(5);

        assertThat(StorageKeyBatches.chunk(keys, 2))
            .containsExactly(
                List.of(keys.get(0), keys.get(1)),
                List.of(keys.get(2), keys.get(3)),
                List.of(keys.get(4)));
    }

    private List<StorageKey> keys(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> new StorageKey("rooms/1/%d.jpg".formatted(index)))
            .toList();
    }
}
