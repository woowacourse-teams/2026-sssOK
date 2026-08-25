/** 서버가 내려주는 실패 응답. 성공과 달리 data 로 감싸지 않고 code·message 를 그대로 준다. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const isApiError = (error: unknown): error is ApiError => error instanceof ApiError;
