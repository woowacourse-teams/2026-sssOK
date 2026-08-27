import {
  loadedBytesOf,
  percentOf,
  snapshotOf,
  startUploadProgress,
  withProgress,
  withTargets,
  withUploaded,
} from "./uploadProgress";

const MB = 1024 * 1024;

const fileOf = (fileName: string, size: number) => {
  const file = new File(["x"], fileName, { type: "image/jpeg" });

  // File 은 내용으로만 크기가 정해진다. 3MB 짜리 문자열을 만들 이유가 없어 값만 바꿔 끼운다.
  Object.defineProperty(file, "size", { value: size });

  return file;
};

const targetOf = (mediaId: number, size: number) => ({
  mediaId,
  fileName: `photo-${mediaId}.jpg`,
  size,
});

const progressOf = (mediaId: number, loaded: number, total: number) => ({
  mediaId,
  fileName: `photo-${mediaId}.jpg`,
  loaded,
  total,
});

describe("startUploadProgress", () => {
  it("발급 전에는 고른 파일 전부를 잠정 분모로 잡는다", () => {
    const state = startUploadProgress([fileOf("a.jpg", 3 * MB), fileOf("b.jpg", 1 * MB)]);

    expect(state.totalCount).toBe(2);
    expect(state.totalBytes).toBe(4 * MB);
    expect(state.completedCount).toBe(0);
    expect(percentOf(state)).toBe(0);
  });
});

describe("withTargets", () => {
  it("거절된 파일을 장수와 바이트 양쪽에서 뺀다", () => {
    const state = startUploadProgress([
      fileOf("a.jpg", 3 * MB),
      fileOf("b.txt", 1 * MB),
      fileOf("c.jpg", 2 * MB),
    ]);

    // b.txt 는 발급이 거절했다. 올라가지 않으므로 분모에서 빠져야 한다.
    const corrected = withTargets(state, [targetOf(1, 3 * MB), targetOf(3, 2 * MB)]);

    expect(corrected.totalCount).toBe(2);
    expect(corrected.totalBytes).toBe(5 * MB);
  });
});

describe("withProgress", () => {
  it("파일별 최신 바이트로 덮어쓴다 — 이벤트를 더하지 않는다", () => {
    let state = withTargets(startUploadProgress([]), [targetOf(1, 10 * MB)]);

    state = withProgress(state, progressOf(1, 2 * MB, 10 * MB));
    state = withProgress(state, progressOf(1, 5 * MB, 10 * MB));

    expect(loadedBytesOf(state)).toBe(5 * MB);
  });

  it("재발급으로 다시 올라가면 퍼센트가 뒤로 물러난다", () => {
    let state = withTargets(startUploadProgress([]), [targetOf(1, 10 * MB), targetOf(2, 10 * MB)]);

    state = withProgress(state, progressOf(1, 9 * MB, 10 * MB));
    expect(percentOf(state)).toBe(45);

    // 9MB 까지 갔다가 PUT 이 깨져 새 URL 로 처음부터 다시 올라간다.
    state = withProgress(state, progressOf(1, 0, 10 * MB));
    expect(percentOf(state)).toBe(0);
  });

  it("발급받은 크기보다 많이 보고돼도 그 이상 세지 않는다", () => {
    let state = withTargets(startUploadProgress([]), [targetOf(1, 10 * MB)]);

    state = withProgress(state, progressOf(1, 12 * MB, 12 * MB));

    expect(loadedBytesOf(state)).toBe(10 * MB);
    expect(percentOf(state)).toBe(100);
  });

  it("발급 전에 이벤트가 오면 이벤트가 알려준 크기를 한도로 쓴다", () => {
    const state = withProgress(startUploadProgress([]), progressOf(1, 5 * MB, 3 * MB));

    expect(loadedBytesOf(state)).toBe(3 * MB);
  });
});

describe("withUploaded", () => {
  it("완료 장수를 올리고 그 파일을 다 보낸 것으로 채운다", () => {
    let state = withTargets(startUploadProgress([]), [targetOf(1, 10 * MB), targetOf(2, 10 * MB)]);

    // 마지막 진행률 이벤트가 99% 에서 끊겨도 완료는 완료다.
    state = withProgress(state, progressOf(1, 9.9 * MB, 10 * MB));
    state = withUploaded(state, { mediaId: 1, fileName: "photo-1.jpg" });

    expect(state.completedCount).toBe(1);
    expect(loadedBytesOf(state)).toBe(10 * MB);
    expect(percentOf(state)).toBe(50);
  });
});

describe("percentOf", () => {
  it("장수가 아니라 바이트로 센다", () => {
    let state = withTargets(startUploadProgress([]), [
      targetOf(1, 1 * MB),
      targetOf(2, 1 * MB),
      targetOf(3, 98 * MB),
    ]);

    // 작은 사진 두 장이 끝났다. 3장 중 2장이지만 회선으로는 2%밖에 안 나갔다.
    state = withUploaded(state, { mediaId: 1, fileName: "photo-1.jpg" });
    state = withUploaded(state, { mediaId: 2, fileName: "photo-2.jpg" });

    expect(snapshotOf(state)).toEqual({ completedCount: 2, totalCount: 3, percent: 2 });
  });

  it("올릴 게 하나도 없으면 0% 다 — 0 으로 나누지 않는다", () => {
    const state = withTargets(startUploadProgress([fileOf("a.txt", 1 * MB)]), []);

    expect(percentOf(state)).toBe(0);
  });

  it("전부 끝나면 100% 다", () => {
    let state = withTargets(startUploadProgress([]), [targetOf(1, 3 * MB), targetOf(2, 7 * MB)]);

    state = withUploaded(state, { mediaId: 1, fileName: "photo-1.jpg" });
    state = withUploaded(state, { mediaId: 2, fileName: "photo-2.jpg" });

    expect(percentOf(state)).toBe(100);
  });

  it("소수점은 버린다 — 다 안 끝났는데 100% 로 보이면 안 된다", () => {
    let state = withTargets(startUploadProgress([]), [targetOf(1, 1000), targetOf(2, 1)]);

    state = withUploaded(state, { mediaId: 1, fileName: "photo-1.jpg" });

    // 1001 바이트 중 1000 바이트 = 99.9%
    expect(percentOf(state)).toBe(99);
  });
});
