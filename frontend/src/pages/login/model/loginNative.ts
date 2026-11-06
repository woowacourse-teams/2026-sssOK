import {
  completeAppleNativeLogin,
  completeKakaoNativeLogin,
  startAppleNativeLogin,
} from "../api/loginNative";
import { requestNativeLogin, type LoginProvider } from "./nativeAuthBridge";

/** 준비 → SDK 인증 → 서버 로그인을 순서대로 조율한다. 인증값은 함수 실행 중에만 보관한다. */
export const loginNative = async (provider: LoginProvider, signal: AbortSignal) => {
  if (provider === "kakao") {
    const token = await requestNativeLogin("kakao", signal);
    return token === null ? null : completeKakaoNativeLogin(token, signal);
  }

  const attempt = await startAppleNativeLogin(signal);
  const code = await requestNativeLogin("apple", signal, attempt.nonce);
  if (code === null) return null;
  if (Date.parse(attempt.expiresAt) <= Date.now()) {
    throw new Error("로그인 시간이 만료됐어요. 다시 시도해주세요.");
  }
  return completeAppleNativeLogin(code, attempt, signal);
};
