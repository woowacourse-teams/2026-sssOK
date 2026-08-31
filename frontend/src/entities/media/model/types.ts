export type PhotoFilter = "all" | "mine" | "others";

/** backend MediaStatus 와 같다. 워커가 처리를 마치면 READY 가 된다. */
export type MediaStatus = "RESERVED" | "PROCESSING" | "READY" | "FAILED";

/**
 * 방에 올라간 사진·영상 한 건.
 *
 * **업로드 직후를 포함한 모든 시점**을 담는다. 등록 직후에는 워커가 아직 안 돌아서
 * `status` 가 `PROCESSING` 이고 파생 URL 두 개가 비어 있다.
 * 처리가 끝난 것만 다루는 곳은 아래 `MediaItem` 을 쓴다.
 */
export interface Media {
  mediaId: number;
  type: "IMAGE" | "VIDEO";
  fileName: string;
  mimeType: string;
  size: number;
  /** 워커가 만드는 값이라 PROCESSING 동안은 null 이다. */
  thumbnailUrl: string | null;
  originalUrl: string | null;
  /** 치수도 워커가 채운다. PROCESSING 동안은 null 이다. */
  width: number | null;
  height: number | null;
  /** 영상만 값이 있다. */
  duration: number | null;
  folderIds: number[];
  uploaderId: number;
  uploaderName: string;
  status: MediaStatus;
  uploadedAt: string;
}

/**
 * 처리가 끝나 화면에 그릴 수 있는 미디어. 목록 조회는 이것만 내려준다.
 * `Media` 를 좁힌 것이라, 서버 스펙이 바뀌면 한 곳만 고치면 된다.
 */
export interface MediaItem extends Media {
  thumbnailUrl: string;
  originalUrl: string;
  width: number;
  height: number;
  status: "READY";
}

export interface MediaList {
  items: MediaItem[];
}
