export const ROUTES = {
  home: "/home",
  privacy: "/privacy",
  terms: "/terms",
  rooms: "/rooms",
  login: "/app/login",
  createRoom: "/rooms/create",
  closedRoom: "/rooms/closed",
  /** 외부 공유 링크에서 브라우저를 확인하는 진입점 */
  roomInvite: (code: string) => `/join/${code}`,
  /** 공유 링크·QR 로 들어오는 진입점 */
  roomEntry: (code: string) => `/rooms/${code}`,
  gallery: (code: string) => `/rooms/${code}/gallery`,
  mediaDetail: (code: string, mediaId: number) => `/rooms/${code}/media/${mediaId}`,
  roomSettings: (code: string) => `/rooms/${code}/settings`,

  admin: "/admin",
  adminLogin: "/admin/login",
  adminFeedbacks: "/admin/feedbacks",
  adminAccounts: "/admin/accounts",
} as const;

/** 라우트 정의에 쓰는 패턴. ROUTES 는 실제 이동에 쓴다. */
export const ROUTE_PATTERNS = {
  home: "/home",
  privacy: "/privacy",
  terms: "/terms",
  rooms: "/rooms",
  login: "/app/login",
  loginCallback: "/app/auth/callback/:provider",
  createRoom: "/rooms/create",
  closedRoom: "/rooms/closed",
  roomInvite: "/join/:code",
  roomEntry: "/rooms/:code",
  gallery: "/rooms/:code/gallery",
  mediaDetail: "/rooms/:code/media/:mediaId",
  roomSettings: "/rooms/:code/settings",

  admin: "/admin",
  adminLogin: "/admin/login",
  adminFeedbacks: "/admin/feedbacks",
  adminAccounts: "/admin/accounts",
} as const;
