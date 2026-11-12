import { z } from "zod";
export const MediaStatusSchema = z.union([
  z.literal("RESERVED"),
  z.literal("PROCESSING"),
  z.literal("READY"),
  z.literal("FAILED"),
]);
export const MediaSchema = z.object({
  mediaId: z.number(),
  type: z.union([z.literal("IMAGE"), z.literal("VIDEO")]),
  fileName: z.string(),
  mimeType: z.string(),
  size: z.number(),
  /** 워커가 만드는 값이라 PROCESSING 동안은 null 이다. */
  thumbnailUrl: z.union([z.string(), z.null()]),
  thumbnailUrlExpiresAt: z.union([z.string(), z.null()]).optional(),
  /**
   * 사진, 영상을 크게 볼 때 쓰는 주소. 어떤 파일을 가리킬지는 서버가 정한다.
   * - 일반 사진: 1600px 로 줄인 프리뷰
   * - GIF, 프리뷰가 없는 예전 사진, 영상: 원본
   * 파일이 아직 안 올라왔거나(RESERVED) 업로드에 실패했으면(FAILED) null 이다.
   */
  displayUrl: z.union([z.string(), z.null()]),
  displayUrlExpiresAt: z.union([z.string(), z.null()]).optional(),
  /** 치수도 워커가 채운다. PROCESSING 동안은 null 이다. */
  width: z.union([z.number(), z.null()]),
  height: z.union([z.number(), z.null()]),
  /** 영상만 값이 있다. */
  duration: z.union([z.number(), z.null()]),
  folderIds: z.array(z.number()),
  uploaderId: z.number(),
  uploaderName: z.string(),
  status: MediaStatusSchema,
  uploadedAt: z.string(),
  /** 목록 조회가 내려주는 값이라 업로드 처리 중 응답에는 없을 수 있다. */
  likeCount: z.number().optional(),
  likedByMe: z.boolean().optional(),
});
export const MediaItemSchema = MediaSchema.extend(
  z.object({
    thumbnailUrl: z.string(),
    displayUrl: z.string(),
    width: z.number(),
    height: z.number(),
    status: z.literal("READY"),
    likeCount: z.number(),
    likedByMe: z.boolean(),
  }).shape,
);
export const MediaListSchema = z.object({
  items: z.array(MediaSchema.extend({ likeCount: z.number(), likedByMe: z.boolean() })),
});
export const MediaDetailSchema = MediaSchema.omit({ thumbnailUrl: true }).extend(
  z.object({
    takenAt: z.union([z.string(), z.null()]),
    location: z.union([
      z.object({
        latitude: z.number(),
        longitude: z.number(),
        name: z.union([z.string(), z.null()]),
      }),
      z.null(),
    ]),
    canDelete: z.boolean(),
  }).shape,
);
export type PhotoFilter = "all" | "mine" | "others" | "liked";
export type PhotoSort = "upload" | "popular";

/** backend MediaStatus 와 같다. 워커가 처리를 마치면 READY 가 된다. */
export type MediaStatus = z.infer<typeof MediaStatusSchema>;

/**
 * 방에 올라간 사진, 영상 한 건
 * 업로드 직후를 포함한 모든 시점을 담는다. 등록 직후에는 워커가 아직 안 돌아서
 * `status` 가 `PROCESSING` 이고 썸네일이 비어 있다.
 * 처리가 끝난 것만 다루는 곳은 아래 `MediaItem` 을 쓴다.
 */
export type Media = z.infer<typeof MediaSchema>;

/**
 * 처리가 끝나 화면에 그릴 수 있는 미디어. 목록 조회는 이것만 내려준다.
 * `Media` 를 좁힌 것이라, 서버 스펙이 바뀌면 한 곳만 고치면 된다.
 */
export type MediaItem = z.infer<typeof MediaItemSchema>;

/** 목록 조회 결과. 처리 중인 미디어도 포함하므로 nullable 이미지 필드를 유지한다. */
export type MediaList = z.infer<typeof MediaListSchema>;

/** 갤러리와 뷰어가 공유하는 사진 한 자리. */
export type GalleryItem =
  | { mediaId: number; type: "local"; file: File; folderIds: number[] }
  | { mediaId: number; type: "server"; media: Media; folderIds: number[] };

/** 단일 조회에는 목록 항목에 촬영 정보·삭제 권한이 더해져 내려온다. */
export type MediaDetail = z.infer<typeof MediaDetailSchema>;
