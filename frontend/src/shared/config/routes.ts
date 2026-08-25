export const ROUTES = {
  home: "/",
  /** 공유 링크·QR 로 들어오는 진입점 */
  roomEntry: (code: string) => `/rooms/${code}`,
  gallery: (code: string) => `/rooms/${code}/gallery`,
} as const;

/** 라우트 정의에 쓰는 패턴. ROUTES 는 실제 이동에 쓴다. */
export const ROUTE_PATTERNS = {
  home: "/",
  roomEntry: "/rooms/:code",
  gallery: "/rooms/:code/gallery",
} as const;
