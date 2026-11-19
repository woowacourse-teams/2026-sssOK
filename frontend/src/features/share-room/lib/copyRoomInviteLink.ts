import { roomInviteUrl } from "./roomInviteUrl";

export const copyRoomInviteLink = async (roomCode: string): Promise<void> => {
  await navigator.clipboard.writeText(roomInviteUrl(roomCode));
};
