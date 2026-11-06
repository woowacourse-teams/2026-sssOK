/** 세션 저장과 화면 이동은 app에서 연결한다. 관리자 인증은 복구 대상이 아니다. */
type Recovery = (token: string) => Promise<string | null>;
let recovery: Recovery | null = null;

export const setAuthRecovery = (next: Recovery) => {
  recovery = next;
  return () => {
    if (recovery === next) recovery = null;
  };
};

export const recoverAccessToken = (token: string) => recovery?.(token) ?? Promise.resolve(null);

// 선제 갱신 직전에 시작한 요청도 현재 토큰으로 이어갈 수 있게 한다.
const replacements = new Map<string, string>();
export const rememberTokenReplacement = (previous: string, next: string) => {
  if (previous === next) return;
  // 여러 번 갱신돼도 이전 요청이 가장 최근 토큰을 사용할 수 있게 한다.
  for (const [token, replacement] of replacements) {
    if (replacement === previous) replacements.set(token, next);
  }
  replacements.set(previous, next);
  if (replacements.size > 64) replacements.delete(replacements.keys().next().value!);
};
export const getTokenReplacement = (token: string) => replacements.get(token);

export const clearTokenReplacements = () => replacements.clear();
