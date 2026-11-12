import { z } from "zod";
import { API_BASE_URL } from "@/shared/config";
import { captureException } from "@/shared/lib";
import { ApiError } from "./ApiError";
import { recoverAccessToken } from "./authRecovery";
import { notifyUnauthorized } from "./unauthorized";

type ExceptionLevel = "fatal" | "error" | "warning";

interface ErrorTrackingOptions {
  level: ExceptionLevel;
  operation: string;
  route: string;
}

interface BaseApiClientOptions extends RequestInit {
  token?: string;

  errorTracking?: ErrorTrackingOptions;
  skipAuthRecovery?: boolean;
}

type ApiClientOptions<T> = BaseApiClientOptions &
  ({ responseType?: "json"; schema: z.ZodType<T> } | { responseType: "empty"; schema?: never });
const ErrorResponseSchema = z.object({
  code: z.string().optional(),
  message: z.string().optional(),
});
type ErrorResponse = z.infer<typeof ErrorResponseSchema>;

const getErrorResponse = async (response: Response): Promise<ErrorResponse> => {
  try {
    const result = ErrorResponseSchema.safeParse(await response.json());
    return result.success ? result.data : {};
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

const shouldCaptureApiError = (error: ApiError): boolean =>
  error.status === 0 || error.status >= 500 || error.code === "INVALID_RESPONSE";

const reportApiError = (
  error: ApiError,
  tracking: ErrorTrackingOptions | undefined,
  method: string,
): void => {
  if (!tracking || !shouldCaptureApiError(error)) return;

  captureException(error, {
    level: error.status === 0 ? "warning" : tracking.level,
    operation: tracking.operation,
    method,
    route: tracking.route,
    status: error.status,
    code: error.code,
  });
};

const throwInvalidResponse = (
  status: number,
  tracking: ErrorTrackingOptions | undefined,
  method: string,
): never => {
  const error = new ApiError(status, "INVALID_RESPONSE", "서버 응답을 이해하지 못했어요.");
  reportApiError(error, tracking, method);
  throw error;
};

/** path 는 접두사를 뺀 경로다 — 접두사는 여기서 한 번만 붙인다. */
export const apiClient = async <T>(
  path: string,
  {
    token,
    headers,
    responseType,
    schema,
    errorTracking,
    skipAuthRecovery = false,
    ...options
  }: ApiClientOptions<T>,
): Promise<T> => {
  const method = options.method?.toUpperCase() ?? "GET";
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
    options.signal?.throwIfAborted();
    const error = new ApiError(0, "NETWORK_ERROR", "네트워크 연결을 확인해주세요.");
    reportApiError(error, errorTracking, method);
    throw error;
  }

  // 스트림 본문은 재전송할 수 없다. 그 외 인증 요청은 한 번만 복구한다.
  const canRetry = !skipAuthRecovery && !(options.body instanceof ReadableStream);
  if (response.status === 401 && token && canRetry) {
    options.signal?.throwIfAborted();
    const renewed = await recoverAccessToken(token);
    options.signal?.throwIfAborted();
    if (renewed)
      return apiClient<T>(path, {
        ...options,
        headers,
        token: renewed,
        ...(responseType === "empty" ? { responseType } : { schema: schema! }),
        errorTracking,
        skipAuthRecovery: true,
      });
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

    const apiError = new ApiError(
      response.status,
      error.code ?? "UNKNOWN_ERROR",
      error.message ?? "API 요청에 실패했습니다.",
      getRetryAfterSeconds(response),
    );
    reportApiError(apiError, errorTracking, method);
    throw apiError;
  }

  // 삭제는 성공 상태 코드로 판정한다. 서버가 빈 본문 대신 JSON을 보내도 무시한다.
  if (responseType === "empty") {
    // 개발 서버의 HTML fallback까지 삭제 성공으로 오인하지는 않는다.
    if (response.headers.get("content-type")?.includes("text/html")) {
      return throwInvalidResponse(response.status, errorTracking, method);
    }
    return undefined as T;
  }

  const body: unknown = await response.json().catch(() => null);

  // 200 인데 본문이 없거나 우리 API 형식이 아니면 서버 응답이 아니다.
  // (목이 준비되기 전 요청이 dev 서버로 새어 index.html 을 받는 경우가 그렇다)
  if (body === null || typeof body !== "object" || !("data" in body)) {
    return throwInvalidResponse(response.status, errorTracking, method);
  }

  const result = schema!.safeParse(body.data);
  if (!result.success) return throwInvalidResponse(response.status, errorTracking, method);
  return result.data;
};
