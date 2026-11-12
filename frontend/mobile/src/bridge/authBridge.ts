import { AuthRequestSchema, type AuthRequest } from "./requestSchemas";
import { authenticateWithSdk, isSdkLoginCancelled, type AuthProvider } from "./sdkAuth";

export type AuthEvent = {
  requestId: string;
  provider: AuthProvider;
} & ({ status: "success"; credential: string } | { status: "cancelled" | "error" });

const readAuthRequest = (raw: string): AuthRequest | null => {
  try {
    const result = AuthRequestSchema.safeParse(JSON.parse(raw));
    return result.success ? result.data : null;
  } catch {
    return null;
  }
};

/** 메시지 검증·중복 실행 방지·응답 전달만 담당한다. */
export const createAuthBridge = (
  emit: (event: AuthEvent) => void,
): { handle(raw: string): boolean; cancelAll(): void } => {
  let busy = false;
  let generation = 0;

  const authenticate = async (request: AuthRequest): Promise<void> => {
    const currentGeneration = generation;
    const reply = (result: AuthEvent): void => {
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
