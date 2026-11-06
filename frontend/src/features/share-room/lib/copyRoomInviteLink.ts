import { ROUTES } from "@/shared/config";

export const copyRoomInviteLink = async (roomCode: string): Promise<void> => {
  // 현재 페이지의 쿼리·해시를 제외한 초대 주소만 공유한다.
  const url = new URL(ROUTES.roomInvite(roomCode), window.location.origin);
  await navigator.clipboard.writeText(url.href);
};
