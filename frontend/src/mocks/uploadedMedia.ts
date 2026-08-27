import type { Media } from "@/entities/media";
import type { GalleryMedia } from "./db";

/**
 * 하이브리드 목이 "실서버로 올라간 실물"을 갤러리에 이어 붙이기 위해 들고 있는 기록.
 *
 * 서버에는 워커가 없어 등록 응답의 `thumbnailUrl`/`originalUrl`/`width`/`height` 가
 * 영영 `null` 이고, 목록 API 도 없어 나중에 다시 물어볼 창구도 없다.
 * 그래서 업로드가 지나갈 때 옆에서 챙겨둔다 —
 * 발급 응답에서 `mediaId → 스토리지 경로`, PUT 에서 `스토리지 경로 → 올린 바이트`.
 *
 * 핸들러(`handlers/hybrid.ts`)는 이 함수들을 부르는 얇은 배선일 뿐이다.
 */

const storagePathByMediaId = new Map<number, string>();
const uploadedUrlByStoragePath = new Map<string, string>();

/**
 * 워커가 못 채운 치수를 메운다. 갤러리 격자가 0 을 만나면 무너진다.
 * 실물 바이트를 해석하지 않으므로 타입별 고정값이다.
 */
const PLACEHOLDER_DIMENSIONS = {
  IMAGE: { width: 4032, height: 3024 },
  VIDEO: { width: 1920, height: 1080 },
} as const;

/** 서명 URL 의 쿼리에는 서명과 만료가 들어 있어 매번 달라진다. 경로만 열쇠로 쓴다. */
const storagePathOf = (uploadUrl: string) => new URL(uploadUrl).pathname;

/** 발급·재발급이 알려준 목적지를 적어둔다. 재발급이면 새 경로가 옛 것을 덮는다. */
export const rememberUploadTarget = (mediaId: number, uploadUrl: string) => {
  storagePathByMediaId.set(mediaId, storagePathOf(uploadUrl));
};

/**
 * 스토리지로 올라간 바이트의 사본을 잡아둔다.
 * 이미지가 아니면 잡지 않는다 — 갤러리가 쓰는 건 그림뿐이다.
 */
export const rememberUploadedBytes = (
  uploadUrl: string,
  contentType: string,
  bytes: ArrayBuffer,
) => {
  if (!contentType.startsWith("image/")) {
    return;
  }

  // 브라우저 밖(테스트)에서는 blob: 주소를 만들 수 없다.
  if (typeof URL.createObjectURL !== "function") {
    return;
  }

  uploadedUrlByStoragePath.set(
    storagePathOf(uploadUrl),
    URL.createObjectURL(new Blob([bytes], { type: contentType })),
  );
};

export const uploadedUrlOf = (mediaId: number): string | null => {
  const path = storagePathByMediaId.get(mediaId);

  return path === undefined ? null : (uploadedUrlByStoragePath.get(path) ?? null);
};

/**
 * 등록 응답의 미디어를 갤러리가 그릴 수 있는 모양으로 맞춘다.
 *
 * 서버가 준 값이 언제나 우선이다 — 워커가 생기면 이 메움은 저절로 비켜난다.
 * 올린 실물이 없으면(영상 등) 주소는 빈 문자열로 남는다. 남의 사진을 끼워 넣지는 않는다.
 */
export const galleryEntryOf = (media: Media): GalleryMedia => {
  const uploaded = uploadedUrlOf(media.mediaId);

  return {
    ...media,
    thumbnailUrl: media.thumbnailUrl ?? uploaded ?? "",
    originalUrl: media.originalUrl ?? uploaded ?? "",
    width: media.width ?? PLACEHOLDER_DIMENSIONS[media.type].width,
    height: media.height ?? PLACEHOLDER_DIMENSIONS[media.type].height,
    status: "READY",
  };
};

/** 테스트끼리 기록이 이어지지 않도록 되돌린다. */
export const resetUploadedMedia = () => {
  storagePathByMediaId.clear();
  uploadedUrlByStoragePath.clear();
};
