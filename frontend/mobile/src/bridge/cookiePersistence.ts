import { requireOptionalNativeModule } from "expo";
import { Platform } from "react-native";

const cookiePersistence =
  Platform.OS === "android"
    ? requireOptionalNativeModule<{ flush: () => Promise<void> }>("SssokCookiePersistence")
    : null;

export const canPersistCookies = cookiePersistence !== null;

export const handleCookiePersistence = (data: string): boolean => {
  try {
    const message: unknown = JSON.parse(data);
    if (
      typeof message !== "object" ||
      message === null ||
      !("type" in message) ||
      message.type !== "auth/persist-cookies"
    ) return false;
  } catch {
    return false;
  }

  void cookiePersistence?.flush().catch(() => {
    console.warn("인증 쿠키의 디스크 저장을 완료하지 못했습니다.");
  });
  return true;
};

/** 페이지 로딩 이후 fetch로 받은 Set-Cookie도 저장한다. 본문·토큰은 읽지 않는다. */
export const COOKIE_PERSISTENCE_SCRIPT = `
(function () {
  if (window.__sssokCookiePersistence) return;
  window.__sssokCookiePersistence = true;
  var originalFetch = window.fetch;
  window.fetch = async function () {
    var response = await originalFetch.apply(this, arguments);
    try {
      var url = new URL(response.url);
      if (response.ok && /^\\/api\\/v1\\/auth(?:\\/|$)/.test(url.pathname)) {
        window.ReactNativeWebView.postMessage(JSON.stringify({ type: "auth/persist-cookies" }));
      }
    } catch (_) {
      // 저장 신호 전송 실패로 이미 성공한 인증 요청을 실패시키지 않는다.
    }
    return response;
  };
})();
true;
`;
