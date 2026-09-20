export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    /**
     * 429 응답의 `Retry-After` 를 초로 환산한 값. 헤더가 없거나 숫자로 읽히지 않으면 undefined 다.
     *
     * 상태 코드만으로는 "언제 다시 되는지" 를 알 수 없어서 화면이 영구 잠김처럼 보인다.
     * 그 값을 들고 올 자리가 여기 말고 없다 — `apiClient` 는 응답을 버리고 에러만 던진다.
     */
    public readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const isApiError = (error: unknown): error is ApiError => error instanceof ApiError;
