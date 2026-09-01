export const ROUTES = {
  home: "/",
  createRoom: "/rooms/create",
  joinRoom: "/rooms/join",
  /** 공유 링크·QR 로 들어오는 진입점 */
  roomEntry: (code: string) => `/rooms/${code}`,
  gallery: (code: string) => `/rooms/${code}/gallery`,
  mediaDetail: (code: string, mediaId: number) => `/rooms/${code}/media/${mediaId}`,
  roomSettings: (code: string) => `/rooms/${code}/settings`,
} as const;

/** 라우트 정의에 쓰는 패턴. ROUTES 는 실제 이동에 쓴다. */
export const ROUTE_PATTERNS = {
  home: "/",
  createRoom: "/rooms/create",
  roomEntry: "/rooms/:code",
  gallery: "/rooms/:code/gallery",
  mediaDetail: "/rooms/:code/media/:mediaId",
  roomSettings: "/rooms/:code/settings",
} as const;
