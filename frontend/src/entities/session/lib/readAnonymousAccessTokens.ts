import { readValidRoomSession } from "./readValidRoomSession";

const ROOM_SESSION_KEY_PREFIX = "sssok.auth:";
/** 서버가 한 번에 받는 익명 토큰 수 (`SocialLoginStartRequest`). */
const MAX_TOKENS = 100;

/**
 * 이 기기에서 방마다 받은 익명 토큰 중 아직 만료되지 않은 것을 모은다.
 *
 * 소셜 로그인 시작 때 넘기면 서버가 그 익명 활동(방 참여·올린 사진)을 회원 계정에 합친다.
 * 만료된 토큰이 하나라도 섞이면 시작 요청이 거절되므로 여기서 미리 거른다.
 */
export const readAnonymousAccessTokens = (now: Date = new Date()): string[] => {
  try {
    // 읽는 도중 만료·손상된 항목이 지워질 수 있어 열쇠부터 떠 둔다.
    const roomCodes = Object.keys(localStorage)
      .filter((key) => key.startsWith(ROOM_SESSION_KEY_PREFIX))
      .map((key) => key.slice(ROOM_SESSION_KEY_PREFIX.length));
    const tokens = roomCodes
      .map((roomCode) => readValidRoomSession(roomCode, now)?.accessToken)
      .filter((token): token is string => typeof token === "string" && token.length > 0);
    return [...new Set(tokens)].slice(0, MAX_TOKENS);
  } catch {
    // 저장소를 못 읽으면 합치지 않고 로그인만 한다.
    return [];
  }
};
