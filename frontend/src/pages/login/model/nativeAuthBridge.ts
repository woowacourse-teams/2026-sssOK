export type LoginProvider = "kakao" | "apple";

export const supportsNativeLogin = (provider: LoginProvider) =>
  !!window.ReactNativeWebView && window.sssokApp?.auth?.includes(provider) === true;

interface NativeAuthResponse {
  requestId: string;
  provider: LoginProvider;
  status: unknown;
  credential?: unknown;
}

const isResponseForRequest = (
  result: unknown,
  requestId: string,
  provider: LoginProvider,
): result is NativeAuthResponse =>
  !!result &&
  typeof result === "object" &&
  "requestId" in result &&
  result.requestId === requestId &&
  "provider" in result &&
  result.provider === provider &&
  "status" in result;

// LAN의 HTTP 개발 주소에서도 사용할 수 있게 secure-context 전용 randomUUID 대신 사용한다.
const createRequestId = () =>
  Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");

/** 요청과 결과를 일회용 ID로 연결하고 완료·취소·페이지 이탈 시 리스너를 정리한다. */
export const requestNativeLogin = (
  provider: LoginProvider,
  signal: AbortSignal,
  nonce?: string,
): Promise<string | null> =>
  new Promise((resolve, reject) => {
    signal.throwIfAborted();
    const requestId = createRequestId();
    const cleanup = () => {
      window.clearTimeout(timer);
      window.removeEventListener("sssok:auth", handle);
      signal.removeEventListener("abort", abort);
    };
    const abort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };
    const handle = (event: Event) => {
      const result: unknown = (event as CustomEvent).detail;
      if (!isResponseForRequest(result, requestId, provider)) return;
      cleanup();
      if (result.status === "cancelled") resolve(null);
      else if (
        result.status === "success" &&
        typeof result.credential === "string" &&
        result.credential.length > 0 &&
        result.credential.length <= 2048
      )
        resolve(result.credential);
      else reject(new Error("로그인을 완료하지 못했어요. 다시 시도해주세요."));
    };
    const timer = window.setTimeout(
      () => {
        cleanup();
        reject(new Error("로그인 시간이 만료됐어요. 다시 시도해주세요."));
      },
      5 * 60 * 1000,
    );
    window.addEventListener("sssok:auth", handle);
    signal.addEventListener("abort", abort, { once: true });
    try {
      if (!supportsNativeLogin(provider)) throw new Error("앱 업데이트 후 다시 시도해주세요.");
      window.ReactNativeWebView!.postMessage(
        JSON.stringify({ type: "auth/login", requestId, provider, ...(nonce && { nonce }) }),
      );
    } catch (error) {
      cleanup();
      reject(error);
    }
  });
