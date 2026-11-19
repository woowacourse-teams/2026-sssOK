import { ROUTES } from "@/shared/config";

/** 현재 페이지의 쿼리·해시를 뺀 초대 주소. `/join/*` 이라 앱이 있으면 받는 쪽에서 앱이 열린다. */
export const roomInviteUrl = (roomCode: string): string =>
  new URL(ROUTES.roomInvite(roomCode), window.location.origin).href;
