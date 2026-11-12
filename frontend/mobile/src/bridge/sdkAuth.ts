import { requireOptionalNativeModule } from "expo";
import { NativeModules, Platform } from "react-native";

export type AuthProvider = "kakao" | "apple";

export const NATIVE_AUTH_PROVIDERS: AuthProvider[] = [
  ...(NativeModules.RNKakaoLogins ? ["kakao" as const] : []),
  ...(Platform.OS === "ios" && requireOptionalNativeModule("ExpoAppleAuthentication")
    ? ["apple" as const]
    : []),
];

const authenticateKakao = async (): Promise<string> => {
  const kakao = await import("@react-native-seoul/kakao-login");
  return (await kakao.login()).accessToken;
};

const authenticateApple = async (nonce?: string): Promise<string | null> => {
  if (Platform.OS !== "ios" || !nonce) throw new Error("Unavailable provider");
  const apple = await import("expo-apple-authentication");
  if (!(await apple.isAvailableAsync())) throw new Error("Unavailable provider");
  const result = await apple.signInAsync({ nonce, requestedScopes: [] });
  return result.authorizationCode;
};

/** SDK만 실행한다. 제공자 인증값을 서비스 토큰으로 교환하는 일은 웹뷰가 맡는다. */
export const authenticateWithSdk = async (
  provider: AuthProvider,
  nonce?: string,
): Promise<string> => {
  const credential =
    provider === "kakao" ? await authenticateKakao() : await authenticateApple(nonce);
  if (!credential || credential.length > 2048) throw new Error("Missing credential");
  return credential;
};

export const isSdkLoginCancelled = (error: unknown): boolean => {
  if (!error || typeof error !== "object") return false;
  if ("code" in error && error.code === "ERR_REQUEST_CANCELED") return true;
  // 카카오 SDK는 취소도 RNKakaoLogins 오류로 반환한다.
  return (
    "message" in error &&
    typeof error.message === "string" &&
    /cancelled|canceled|userCancel/i.test(error.message)
  );
};
