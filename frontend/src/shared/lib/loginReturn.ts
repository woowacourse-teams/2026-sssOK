const KEY = "sssok.login-return";
/** 로그인하러 갔다가 그만두고 한참 뒤 다른 곳에서 로그인했을 때, 옛 방으로 끌려가지 않게 한다. */
const TTL_MS = 30 * 60 * 1000;

/** 서비스 안의 경로만 받는다. `//host` 같은 외부 주소와 로그인 화면 자신은 버린다. */
const isSafePath = (path: unknown): path is string =>
  typeof path === "string" &&
  path.startsWith("/") &&
  !path.startsWith("//") &&
  !path.includes("\\") &&
  !path.startsWith("/app/");

/**
 * 로그인을 마치고 돌아올 화면을 기억한다.
 * 카카오 인증은 다른 사이트를 거쳐 같은 탭으로 돌아오므로, 라우터 상태 대신 탭 저장소에 둔다.
 */
export const rememberLoginReturn = (path: string) => {
  if (!isSafePath(path)) return;
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ path, savedAt: Date.now() }));
  } catch {
    // 저장하지 못하면 로그인 뒤 방 목록으로 갈 뿐이다.
  }
};

/** 기억해 둔 화면을 꺼내고 지운다. 없거나 오래됐거나 안전하지 않으면 null. */
export const takeLoginReturn = (): string | null => {
  try {
    const raw = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    if (!raw) return null;
    const { path, savedAt } = JSON.parse(raw) as { path?: unknown; savedAt?: unknown };
    if (typeof savedAt !== "number" || Date.now() - savedAt > TTL_MS) return null;
    return isSafePath(path) ? path : null;
  } catch {
    return null;
  }
};
