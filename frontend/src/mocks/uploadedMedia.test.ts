import {
  galleryEntryOf,
  rememberUploadTarget,
  rememberUploadedBytes,
  resetUploadedMedia,
  uploadedUrlOf,
} from "./uploadedMedia";

const STORAGE = "https://acc.r2.cloudflarestorage.com/bucket/rooms/42";

/** 실서버가 등록 직후에 주는 모양. 워커가 안 돌아 파생값이 전부 비어 있다. */
const justRegistered = (mediaId: number) => ({
  mediaId,
  type: "IMAGE" as const,
  fileName: `${mediaId}.png`,
  mimeType: "image/png",
  size: 70,
  thumbnailUrl: null,
  originalUrl: null,
  width: null,
  height: null,
  duration: null,
  folderIds: [],
  uploaderId: 7,
  uploaderName: "올린사람",
  status: "PROCESSING" as const,
  uploadedAt: "2026-08-27T17:43:23Z",
});

const uploadedTo = (mediaId: number, key: string, bytes: number[]) => {
  const url = `${STORAGE}/${key}.png?X-Amz-Signature=sig`;

  rememberUploadTarget(mediaId, url);
  rememberUploadedBytes(url, "image/png", new Uint8Array(bytes).buffer);
};

describe("하이브리드 목이 챙겨두는 업로드 실물", () => {
  afterEach(resetUploadedMedia);

  it("워커가 안 채운 상태와 치수를 갤러리가 그릴 수 있게 메운다", () => {
    const entry = galleryEntryOf(justRegistered(1));

    // 실서버가 준 PROCESSING + null 을 그대로 두면 갤러리가 한 장도 못 그린다.
    expect(entry.status).toBe("READY");
    expect(entry.width).toBeGreaterThan(0);
    expect(entry.height).toBeGreaterThan(0);
  });

  it("올린 실물을 썸네일로 쓴다 — 남의 사진을 끼워 넣지 않는다", () => {
    uploadedTo(1, "a", [1, 2, 3]);

    const entry = galleryEntryOf(justRegistered(1));

    expect(entry.thumbnailUrl).toMatch(/^blob:/);
    expect(entry.originalUrl).toBe(entry.thumbnailUrl);
  });

  it("미디어마다 자기가 올린 바이트를 쓴다", () => {
    uploadedTo(1, "a", [1, 2, 3]);
    uploadedTo(2, "b", [9, 9, 9, 9]);

    expect(uploadedUrlOf(1)).not.toBe(uploadedUrlOf(2));
  });

  it("올린 실물이 없으면 빈 주소로 둔다", () => {
    const entry = galleryEntryOf(justRegistered(1));

    expect(entry.thumbnailUrl).toBe("");
  });

  it("서버가 파생값을 채워주면 그쪽이 이긴다", () => {
    uploadedTo(1, "a", [1, 2, 3]);

    const entry = galleryEntryOf({
      ...justRegistered(1),
      thumbnailUrl: "https://cdn.example.com/thumb.jpg",
      originalUrl: "https://cdn.example.com/original.jpg",
      width: 800,
      height: 600,
    });

    // 워커가 생기면 이 메움은 저절로 비켜나야 한다.
    expect(entry.thumbnailUrl).toBe("https://cdn.example.com/thumb.jpg");
    expect(entry.width).toBe(800);
  });

  it("재발급으로 키가 갈리면 새로 올린 것을 쓴다", () => {
    uploadedTo(1, "old", [1, 2, 3]);
    const first = uploadedUrlOf(1);

    uploadedTo(1, "new", [4, 5, 6]);

    // 옛 키로 올라간 바이트가 새 파일을 덮으면 안 된다.
    expect(uploadedUrlOf(1)).not.toBe(first);
  });

  it("서명 쿼리가 달라도 같은 경로면 같은 실물이다", () => {
    rememberUploadTarget(1, `${STORAGE}/a.png?X-Amz-Signature=first`);
    rememberUploadedBytes(`${STORAGE}/a.png?X-Amz-Signature=second`, "image/png", new ArrayBuffer(3));

    expect(uploadedUrlOf(1)).toMatch(/^blob:/);
  });

  it("이미지가 아니면 잡아두지 않는다", () => {
    const url = `${STORAGE}/movie.mp4?X-Amz-Signature=sig`;

    rememberUploadTarget(1, url);
    rememberUploadedBytes(url, "video/mp4", new ArrayBuffer(3));

    expect(uploadedUrlOf(1)).toBeNull();
  });
});
