/** backend RoomCode 와 같은 규칙. 혼동하기 쉬운 0, 1, I, O 는 빠져 있다. */
const ROOM_CODE_PATTERN = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/;
const ROOM_PATH_PATTERN = /\/(?:join|rooms)\/([^/?#]+)/;

export const parseRoomCode = (input: string): string | null => {
  const value = input.trim();
  const candidate = ROOM_PATH_PATTERN.exec(value)?.[1] ?? value;
  const code = candidate.toUpperCase();

  return ROOM_CODE_PATTERN.test(code) ? code : null;
};
