export type PhotoFilter = "all" | "mine" | "others";

/** backend MediaStatus 와 같다. 워커가 처리를 마치면 READY 가 된다. */
export type MediaStatus = "RESERVED" | "PROCESSING" | "READY" | "FAILED";

/**
 * 방에 올라간 사진, 영상 한 건
 * 업로드 직후를 포함한 모든 시점을 담는다. 등록 직후에는 워커가 아직 안 돌아서
 * `status` 가 `PROCESSING` 이고 썸네일이 비어 있다.
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
  thumbnailUrlExpiresAt?: string | null;
  /**
   * 사진, 영상을 크게 볼 때 쓰는 주소. 어떤 파일을 가리킬지는 서버가 정한다.
   * - 일반 사진: 1600px 로 줄인 프리뷰
   * - GIF, 프리뷰가 없는 예전 사진, 영상: 원본
   * 파일이 아직 안 올라왔거나(RESERVED) 업로드에 실패했으면(FAILED) null 이다.
   */
  displayUrl: string | null;
  displayUrlExpiresAt?: string | null;
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
  /** 목록 조회가 내려주는 값이라 업로드 처리 중 응답에는 없을 수 있다. */
  likeCount?: number;
  likedByMe?: boolean;
}

/**
 * 처리가 끝나 화면에 그릴 수 있는 미디어. 목록 조회는 이것만 내려준다.
 * `Media` 를 좁힌 것이라, 서버 스펙이 바뀌면 한 곳만 고치면 된다.
 */
export interface MediaItem extends Media {
  thumbnailUrl: string;
  displayUrl: string;
  width: number;
  height: number;
  status: "READY";
  likeCount: number;
  likedByMe: boolean;
}

/** 목록 조회 결과. 서버가 방의 미디어를 최신순으로 전부 내려준다. */
export interface MediaList {
  items: MediaItem[];
}

/** 갤러리와 뷰어가 공유하는 사진 한 자리. */
export type GalleryItem =
  | { mediaId: number; type: "local"; file: File; folderIds: number[] }
  | { mediaId: number; type: "server"; media: Media; folderIds: number[] };

/** 단일 조회에는 목록 항목에 촬영 정보·삭제 권한이 더해져 내려온다. */
export interface MediaDetail extends Omit<Media, "thumbnailUrl"> {
  takenAt: string | null;
  location: {
    latitude: number;
    longitude: number;
    name: string;
  } | null;
  canDelete: boolean;
}
