import { API_BASE_URL } from "@/shared/config";
import { ApiError } from "./ApiError";
import { notifyUnauthorized } from "./unauthorized";

interface ApiClientOptions extends RequestInit {
  token?: string;
  responseType?: "json" | "empty";
}

interface ErrorResponse {
  code?: string;
  message?: string;
}

/** 성공 응답은 항상 data 로 한 겹 감싸여 온다 (backend ApiResponse<T>). */
interface ApiResponse<T> {
  data: T;
}

const getErrorResponse = async (response: Response): Promise<ErrorResponse> => {
  try {
    return (await response.json()) as ErrorResponse;
  } catch {
    return {};
  }
};

/**
 * `Retry-After` 를 초로 읽는다. 읽히지 않으면 undefined 다.
 *
 * 서버는 초 단위 숫자를 보내지만(HTTP 명세는 날짜도 허용한다), 프록시가 헤더를 떨어뜨리거나
 * 값이 깨져 올 수 있다. 그때 0 으로 떨어뜨리면 화면이 "0초 후 재시도" 로 굳으므로,
 * 못 읽은 것과 "0초" 를 구분해 부르는 쪽이 카운트다운을 포기할 수 있게 한다.
 */
const getRetryAfterSeconds = (response: Response): number | undefined => {
  const raw = response.headers.get("Retry-After");

  if (raw === null) return undefined;

  const seconds = Number(raw.trim());

  if (!Number.isFinite(seconds) || seconds < 0) return undefined;

  return seconds;
};

/** path 는 접두사를 뺀 경로다 — 접두사는 여기서 한 번만 붙인다. */
export const apiClient = async <T>(
  path: string,
  { token, headers, responseType = "json", ...options }: ApiClientOptions = {},
): Promise<T> => {
  const requestHeaders = new Headers(headers);

  if (token) {
    requestHeaders.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: requestHeaders,
    });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "네트워크 연결을 확인해주세요.");
  }

  if (!response.ok) {
    const error = await getErrorResponse(response);

    /*
     * 인증 자체가 깨졌다 — 토큰이 만료됐거나 서명이 맞지 않거나 형식이 틀렸다.
     * 이 토큰으로는 무엇을 불러도 같은 답이 오므로, 던지기 전에 한 번 알린다 (#149).
     *
     * 화면마다 알아보게 두면 하나씩 빠뜨린다. 인가 실패(403)와 달리 401 은 사용자가
     * 그 화면에서 할 수 있는 일이 없어서, 판단이 갈릴 여지도 없다.
     */
    if (response.status === 401 && token) {
      notifyUnauthorized(token);
    }

    throw new ApiError(
      response.status,
      error.code ?? "UNKNOWN_ERROR",
      error.message ?? "API 요청에 실패했습니다.",
      getRetryAfterSeconds(response),
    );
  }

  // 삭제는 성공 상태 코드로 판정한다. 서버가 빈 본문 대신 JSON을 보내도 무시한다.
  if (responseType === "empty") {
    // 개발 서버의 HTML fallback까지 삭제 성공으로 오인하지는 않는다.
    if (response.headers.get("content-type")?.includes("text/html")) {
      throw new ApiError(response.status, "INVALID_RESPONSE", "서버 응답을 이해하지 못했어요.");
    }
    return undefined as T;
  }

  const body: unknown = await response.json().catch(() => null);

  // 200 인데 본문이 없거나 우리 API 형식이 아니면 서버 응답이 아니다.
  // (목이 준비되기 전 요청이 dev 서버로 새어 index.html 을 받는 경우가 그렇다)
  if (body === null || typeof body !== "object" || !("data" in body)) {
    throw new ApiError(response.status, "INVALID_RESPONSE", "서버 응답을 이해하지 못했어요.");
  }

  return (body as ApiResponse<T>).data;
};
