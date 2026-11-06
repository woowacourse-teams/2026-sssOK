import { authenticateWithSdk, isSdkLoginCancelled, type AuthProvider } from "./sdkAuth";

type AuthRequest = {
  requestId: string;
} & ({ provider: "kakao" } | { provider: "apple"; nonce: string });

export type AuthEvent = {
  requestId: string;
  provider: AuthProvider;
} & ({ status: "success"; credential: string } | { status: "cancelled" | "error" });

const readAuthRequest = (raw: string): AuthRequest | null => {
  let request: unknown;
  try {
    request = JSON.parse(raw);
  } catch {
    return null;
  }
  if (
    !request ||
    typeof request !== "object" ||
    !("type" in request) ||
    request.type !== "auth/login" ||
    !("requestId" in request) ||
    typeof request.requestId !== "string" ||
    !/^[A-Za-z0-9-]{1,64}$/.test(request.requestId) ||
    !("provider" in request)
  )
    return null;
  const { requestId, provider } = request;
  if (provider === "kakao") return { requestId, provider };
  if (
    provider === "apple" &&
    "nonce" in request &&
    typeof request.nonce === "string" &&
    /^[A-Za-z0-9_-]{43}$/.test(request.nonce)
  )
    return { requestId, provider, nonce: request.nonce };
  return null;
};

/** 메시지 검증·중복 실행 방지·응답 전달만 담당한다. */
export const createAuthBridge = (emit: (event: AuthEvent) => void) => {
  let busy = false;
  let generation = 0;

  const authenticate = async (request: AuthRequest) => {
    const currentGeneration = generation;
    const reply = (result: AuthEvent) => {
      if (currentGeneration === generation) emit(result);
    };
    const identity = { requestId: request.requestId, provider: request.provider };
    if (busy) {
      reply({ ...identity, status: "error" });
      return;
    }
    busy = true;
    try {
      const credential = await authenticateWithSdk(
        request.provider,
        request.provider === "apple" ? request.nonce : undefined,
      );
      reply({ ...identity, status: "success", credential });
    } catch (error) {
      // SDK 오류 원문에 인증값이 포함될 수 있어 웹뷰에는 상태만 전달한다.
      reply({ ...identity, status: isSdkLoginCancelled(error) ? "cancelled" : "error" });
    } finally {
      busy = false;
    }
  };

  return {
    handle(raw: string): boolean {
      const request = readAuthRequest(raw);
      if (!request) return false;
      void authenticate(request);
      return true;
    },
    cancelAll() {
      // 페이지가 바뀐 뒤에는 이전 인증 결과를 새 페이지로 보내지 않는다.
      generation += 1;
    },
  };
};
