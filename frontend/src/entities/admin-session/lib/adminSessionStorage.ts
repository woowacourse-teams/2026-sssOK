import type { AdminSession } from "../model/types";

/**
 * 관리자 세션은 방 세션(`sssok.auth:{roomCode}`)과 키를 완전히 분리한다.
 *
 * 둘은 다른 주체다 — 방 세션은 방마다 새로 만드는 익명 회원이고, 관리자는 앱 전체에 하나뿐인
 * 계정이다. 같은 접두사를 쓰면 방 세션을 훑는 `findRoomCodeByToken` 이 관리자 세션을
 * "어떤 방의 세션" 으로 잘못 집어 든다.
 */
const ADMIN_SESSION_KEY = "sssok.admin";

export const saveAdminSession = (session: AdminSession) => {
  localStorage.setItem(ADMIN_SESSION_KEY, JSON.stringify(session));
};

export const getAdminSession = (): AdminSession | null => {
  const raw = localStorage.getItem(ADMIN_SESSION_KEY);

  if (!raw) return null;

  try {
    return JSON.parse(raw) as AdminSession;
  } catch {
    // 손상된 값은 되살릴 방법이 없다. 지우고 로그인부터 다시 밟게 한다.
    localStorage.removeItem(ADMIN_SESSION_KEY);
    return null;
  }
};

export const removeAdminSession = () => {
  localStorage.removeItem(ADMIN_SESSION_KEY);
};

/**
 * 조회 + 만료 검사까지. `expiresAt` 이 지났으면 지우고 null 을 돌려준다.
 *
 * 만료를 로컬에서 먼저 거르는 건 죽은 토큰으로 요청을 한 번 더 보내지 않기 위해서다.
 * 로컬 판단이 최종 판단은 아니다 — 서버가 거부하면 그쪽이 이긴다.
 */
export const readValidAdminSession = (): AdminSession | null => {
  const session = getAdminSession();

  if (!session) return null;

  const expiresAt = Date.parse(session.expiresAt);

  // 만료 시각을 못 읽으면 살아 있다고 보지 않는다 — 판단할 수 없는 세션은 없는 것과 같다.
  if (Number.isNaN(expiresAt) || expiresAt <= Date.now()) {
    removeAdminSession();
    return null;
  }

  return session;
};
