import { isApiError } from "@/shared/api";

/**
 * 못 받은 이유를 사용자가 읽을 문장으로 바꾼다 (#120 · #121 완료 조건).
 *
 * **사유마다 사용자가 할 수 있는 일이 다르다.** 아직 처리 중인 사진은 잠시 뒤에 다시 누르면
 * 되고, 없는 사진은 몇 번을 눌러도 똑같다. 그래서 문구만 나누는 게 아니라 재시도를 내줄지도
 * 여기서 함께 정한다 — 두 판단이 같은 상태 코드를 보고 갈리므로 떨어뜨려 두면 어긋난다.
 */

const MESSAGE_BY_STATUS: Record<number, string> = {
  /** 응답 자체를 못 받았다. 회선이 끊겼거나 스토리지 CORS 에 막힌 경우다. */
  0: "네트워크 연결을 확인해주세요",
  404: "찾을 수 없어요",
  409: "아직 처리 중이에요",
  410: "기한이 지났어요",
  429: "받는 중인 요청이 많아요",
};

/** 사유를 특정할 수 없을 때. 섞인 실패와 모르는 상태 코드가 여기로 온다. */
export const DOWNLOAD_FALLBACK_MESSAGE = "받지 못했어요";

/**
 * 다시 눌러볼 만한 실패인지.
 *
 * 404 만 빠진다 — 없거나 지워진 사진은 기다린다고 생기지 않는다.
 * 410(기한 지남)은 재시도로 새 잡을 만들면 되므로 포함한다 (#121 완료 조건).
 */
const RETRYABLE_STATUSES = [0, 409, 410, 429];

export const downloadMessageOfStatus = (status: number) =>
  MESSAGE_BY_STATUS[status] ?? DOWNLOAD_FALLBACK_MESSAGE;

export const isRetryableStatus = (status: number) => RETRYABLE_STATUSES.includes(status);

/**
 * 던져진 예외에서 문구를 뽑는다. `ApiError` 가 아니면 상태 코드를 알 수 없으니
 * 기본 문구로 떨어뜨린다 — 원인 모를 실패에 그럴듯한 사유를 붙이지 않는다.
 */
export const downloadMessageOfError = (error: unknown) =>
  isApiError(error) ? downloadMessageOfStatus(error.status) : DOWNLOAD_FALLBACK_MESSAGE;

export const isRetryableError = (error: unknown) =>
  isApiError(error) ? isRetryableStatus(error.status) : true;
