/**
 * 목 핸들러끼리 공유하는 기록.
 *
 * 업로드(`handlers/upload.ts`)가 등록한 미디어를 갤러리 목록(`handlers/room.ts`)이 봐야 하는데,
 * 업로드 목이 이미 방 목을 import 하고 있어 서로 부르면 순환이 된다.
 * 그래서 공유하는 것만 여기로 내리고, 이 파일은 어떤 핸들러도 import 하지 않는다.
 */

/**
 * 등록이 끝나 갤러리에 그릴 수 있는 미디어.
 * `entities/media` 의 `MediaItem` 과 같은 모양이다 — 파생 URL 이 있고 `READY` 다.
 */
export interface GalleryMedia {
  mediaId: number;
  type: "IMAGE" | "VIDEO";
  fileName: string;
  mimeType: string;
  size: number;
  thumbnailUrl: string;
  originalUrl: string;
  width: number;
  height: number;
  duration: number | null;
  folderIds: number[];
  uploaderId: number;
  uploaderName: string;
  status: "READY";
  uploadedAt: string;
}

/**
 * 목은 올라온 바이트를 해석하지 않아 실제 썸네일을 만들 수 없다.
 * 씨앗만 `mediaId` 로 고정해서, 같은 미디어는 언제 봐도 같은 그림이 나오게 한다.
 */
export const thumbnailUrlOf = (mediaId: number) =>
  `https://picsum.photos/seed/sssok-${mediaId}/600/700`;

export const originalUrlOf = (mediaId: number, type: "IMAGE" | "VIDEO") =>
  type === "VIDEO"
    ? `https://cdn.example.com/rooms/1024/${mediaId}.mp4`
    : `https://picsum.photos/seed/sssok-${mediaId}/1200/1400`;

/** 방 번호 → 이번 세션에 등록된 미디어. 최신이 앞이다. */
const registeredByRoom = new Map<number, GalleryMedia[]>();

/**
 * 완료 등록이 끝난 미디어를 목록에 올린다.
 *
 * 실제 서버라면 워커가 파생 URL 을 만든 **뒤에야** 목록에 뜬다. 목에는 워커가 없어서
 * 등록과 동시에 올린다 — 안 그러면 올린 사진이 갤러리에 영영 나타나지 않는다.
 */
export const addRegisteredMedia = (roomId: number, media: GalleryMedia) => {
  registeredByRoom.set(roomId, [media, ...(registeredByRoom.get(roomId) ?? [])]);
};

export const registeredMediaOf = (roomId: number) => registeredByRoom.get(roomId) ?? [];

/**
 * 올린 사진은 `blob:` 주소로 들어온다. 문서가 살아 있는 한 계속 잡고 있어서,
 * 버릴 때 같이 풀어주지 않으면 그 바이트가 메모리에 남는다.
 */
const revokeIfObjectUrl = (url: string) => {
  if (url.startsWith("blob:") && typeof URL.revokeObjectURL === "function") {
    URL.revokeObjectURL(url);
  }
};

/** 테스트끼리 등록 기록이 이어지지 않도록 되돌린다. */
export const resetRegisteredMedia = () => {
  for (const mediaList of registeredByRoom.values()) {
    mediaList.forEach(({ thumbnailUrl, originalUrl }) => {
      revokeIfObjectUrl(thumbnailUrl);
      // 사진은 썸네일과 원본이 같은 주소다. 두 번 풀어도 문제되지 않는다.
      revokeIfObjectUrl(originalUrl);
    });
  }

  registeredByRoom.clear();
};
