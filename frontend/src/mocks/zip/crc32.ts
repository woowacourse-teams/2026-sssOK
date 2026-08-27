/**
 * ZIP 이 각 항목마다 요구하는 CRC-32 체크섬(IEEE 802.3).
 *
 * 압축은 안 해도 **체크섬만은 반드시 맞아야 한다.** 0 을 넣어두면 파일 탐색기는 열어주지만
 * 맥 아카이브 유틸리티와 알집은 "손상된 파일"로 거절한다.
 *
 * 파일 전체를 한 번에 받지 않고 조각으로 이어 계산한다 — 80MB 영상을 통째로 메모리에
 * 올리지 않기 위해서다. 부르는 쪽이 `update` 를 반복하고 마지막에 `value` 를 읽는다.
 */

/**
 * 바이트 한 개당 8번 도는 비트 연산을 미리 256칸에 접어둔 표.
 * 표 없이 계산하면 사진 한 장(수 MB)에 수천만 번 반복이라 메인 스레드가 눈에 띄게 멈춘다.
 */
const TABLE = (() => {
  const table = new Uint32Array(256);

  for (let index = 0; index < 256; index += 1) {
    let value = index;

    for (let bit = 0; bit < 8; bit += 1) {
      // 0xEDB88320 은 IEEE 다항식을 뒤집은 값이다. ZIP·PNG·gzip 이 모두 이걸 쓴다.
      value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
    }

    table[index] = value >>> 0;
  }

  return table;
})();

export class Crc32 {
  /** 규격이 정한 시작값. 0 에서 시작하면 앞에 붙은 0 바이트를 구분하지 못한다. */
  private state = 0xffffffff;

  update(chunk: Uint8Array) {
    let state = this.state;

    for (let index = 0; index < chunk.length; index += 1) {
      state = TABLE[(state ^ chunk[index]) & 0xff] ^ (state >>> 8);
    }

    this.state = state;

    return this;
  }

  /** 최종 뒤집기까지 마친 부호 없는 32비트 값. 여러 번 읽어도 결과가 같다. */
  get value() {
    return (this.state ^ 0xffffffff) >>> 0;
  }
}

export const crc32Of = (bytes: Uint8Array) => new Crc32().update(bytes).value;
