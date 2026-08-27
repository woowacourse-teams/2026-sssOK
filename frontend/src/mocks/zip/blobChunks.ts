/**
 * Blob 을 조각 단위로 읽는다.
 *
 * `blob.arrayBuffer()` 는 파일 전체를 **메모리로 복사한다.** 80MB 영상 여러 개를 zip 으로
 * 묶는 동안 그 복사본이 동시에 살아 있으면 아이폰 사파리가 탭을 통째로 버린다.
 * 조각으로 읽으면 어느 시점에도 조각 하나만 메모리에 있다.
 *
 * `Blob.stream()` 이 없는 환경(구형 사파리·일부 테스트 환경)에서는 `slice` 로 잘라 읽는다.
 * 자른 Blob 은 원본을 참조만 하므로, 잘라내는 것만으로는 바이트가 복사되지 않는다.
 */

/** 8MB. 사파리가 한 번에 무리 없이 잡는 크기이면서, 조각 수가 너무 늘지 않는 지점이다. */
const FALLBACK_CHUNK_SIZE = 8 * 1024 * 1024;

export async function* blobChunks(blob: Blob): AsyncGenerator<Uint8Array> {
  if (typeof blob.stream === "function") {
    const reader = blob.stream().getReader();

    try {
      for (;;) {
        const { done, value } = await reader.read();

        if (done) {
          return;
        }

        yield value;
      }
    } finally {
      // 중간에 빠져나가도(throw·break) 원본 스트림을 놓아준다.
      reader.releaseLock();
    }

    return;
  }

  for (let offset = 0; offset < blob.size; offset += FALLBACK_CHUNK_SIZE) {
    const slice = blob.slice(offset, Math.min(offset + FALLBACK_CHUNK_SIZE, blob.size));

    yield new Uint8Array(await slice.arrayBuffer());
  }
}
