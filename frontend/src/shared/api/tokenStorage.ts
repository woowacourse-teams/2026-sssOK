const ACCESS_TOKEN_KEY = "sssok.accessToken";

export const tokenStorage = {
  get(): string | null {
    try {
      return localStorage.getItem(ACCESS_TOKEN_KEY);
    } catch {
      // 사파리 프라이빗 모드 등 저장소 접근이 막힌 환경
      return null;
    }
  },

  set(token: string) {
    try {
      localStorage.setItem(ACCESS_TOKEN_KEY, token);
    } catch {
      // 저장에 실패해도 이번 세션은 메모리의 토큰으로 진행한다
    }
  },

  clear() {
    try {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
    } catch {
      // 지울 수 없으면 그대로 둔다
    }
  },
};
