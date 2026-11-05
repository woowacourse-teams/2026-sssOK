import { isApiError } from "@/shared/api";
import { isAppWebView } from "@/shared/lib";
import { ORGANIZE_PHOTO_LIMIT } from "../config";

/**
 * 접수·재시도를 거절당했을 때 보여 줄 문구 (2026-11-04 API 명세서).
 *
 * 제한·권한 정책
 * - 분석 가능한 사진 1~200장
 * - 익명은 방마다 AI 정리 1회 체험, 회원은 횟수 대신 요청 간격으로 제한(429)
 * - 방장·참여자 모두 시작할 수 있다
 */
export const ORGANIZE_ERROR_CODES = {
  trialExhausted: "ORGANIZE_TRIAL_EXHAUSTED",
  rateLimited: "ORGANIZE_RATE_LIMITED",
  tooManyPhotos: "ORGANIZE_TOO_MANY_PHOTOS",
  noAnalyzablePhotos: "ORGANIZE_NO_ANALYZABLE_PHOTOS",
  inProgress: "ORGANIZE_JOB_IN_PROGRESS",
  unavailable: "ORGANIZE_UNAVAILABLE",
  needsClarification: "ORGANIZE_NEEDS_CLARIFICATION",
  unsupportedRequest: "ORGANIZE_UNSUPPORTED_REQUEST",
  retryNotAllowed: "ORGANIZE_RETRY_NOT_ALLOWED",
  folderNameConflict: "ORGANIZE_FOLDER_NAME_CONFLICT",
  targetChanged: "ORGANIZE_TARGET_CHANGED",
  alreadyApplied: "ORGANIZE_ALREADY_APPLIED",
  notReady: "ORGANIZE_JOB_NOT_READY",
  reviewExpired: "ORGANIZE_REVIEW_EXPIRED",
  resultExpired: "ORGANIZE_RESULT_EXPIRED",
} as const;

export const waitText = (seconds: number) => {
  if (seconds < 60) return `${Math.max(1, Math.ceil(seconds))}초`;
  const minutes = Math.ceil(seconds / 60);
  return minutes < 60 ? `${minutes}분` : `${Math.ceil(minutes / 60)}시간`;
};

const FALLBACK = "정리를 시작하지 못했어요. 잠시 뒤 다시 시도해 주세요.";

export const organizeErrorMessage = (error: unknown) => {
  if (!isApiError(error)) return FALLBACK;

  switch (error.code) {
    case ORGANIZE_ERROR_CODES.trialExhausted:
      // 웹에서는 로그인할 곳이 앱이라 앱으로 보내고, 앱 안에서는 로그인만 권한다.
      return isAppWebView()
        ? "로그인하지 않으면 더 이상 AI 정리를 쓸 수 없어요.\n로그인해 주세요."
        : "이제 AI 정리는 앱에서 이어서 쓸 수 있어요.\n앱을 설치하고 로그인해 주세요.";
    case ORGANIZE_ERROR_CODES.rateLimited:
      return error.retryAfterSeconds === undefined
        ? "조금 뒤에 다시 정리할 수 있어요."
        : `${waitText(error.retryAfterSeconds)} 뒤에 다시 정리할 수 있어요.`;
    case ORGANIZE_ERROR_CODES.tooManyPhotos:
      return `사진이 ${ORGANIZE_PHOTO_LIMIT}장을 넘으면 아직 나눌 수 없어요.`;
    case ORGANIZE_ERROR_CODES.noAnalyzablePhotos:
      return "나눌 수 있는 사진이 아직 없어요.";
    case ORGANIZE_ERROR_CODES.inProgress:
      return "다른 사람이 정리하고 있어요. 끝나면 다시 시도해 주세요.";
    case ORGANIZE_ERROR_CODES.unavailable:
      return "지금은 사진 정리를 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요.";
    case ORGANIZE_ERROR_CODES.needsClarification:
      return "어떻게 정리할지 조금 더 자세히 써 주세요. 예: 사람별로 나눠 줘";
    case ORGANIZE_ERROR_CODES.unsupportedRequest:
      return "아직 할 수 없는 정리예요. ‘자동으로 정리하기’에서 골라 주세요.";
    case ORGANIZE_ERROR_CODES.retryNotAllowed:
      return "이 작업은 더 다시 시도할 수 없어요. 새로 정리해 주세요.";
    default:
      return error.status === 0 ? "네트워크 연결을 확인해 주세요." : FALLBACK;
  }
};

/** 작업을 멈추거나 결과를 취소하지 못했을 때 보여 줄 문구. 네트워크 탓이면 연결을 먼저 확인하게 한다. */
export const cancelErrorMessage = (error: unknown, fallback: string) =>
  isApiError(error) && error.status === 0 ? "네트워크 연결을 확인해 주세요." : fallback;

const APPLY_FALLBACK = "폴더를 만들지 못했어요. 다시 시도해 주세요.";

/**
 * 반영이 끝난 것으로 봐야 하는 거절. 이미 반영됐거나 검토할 수 없게 돼, 다시 눌러도 같은 답이 온다.
 * 이때는 화면을 붙잡지 않고 현재 상태를 다시 읽는다.
 */
export const isApplyClosed = (error: unknown) =>
  isApiError(error) &&
  (
    [
      ORGANIZE_ERROR_CODES.alreadyApplied,
      ORGANIZE_ERROR_CODES.notReady,
      ORGANIZE_ERROR_CODES.reviewExpired,
      ORGANIZE_ERROR_CODES.resultExpired,
    ] as string[]
  ).includes(error.code);

/** 폴더 반영을 거절당했을 때 검토 화면에 띄울 문구. 서버는 어느 경우에도 일부만 반영하지 않는다. */
export const applyErrorMessage = (error: unknown) => {
  if (!isApiError(error)) return APPLY_FALLBACK;

  switch (error.code) {
    case ORGANIZE_ERROR_CODES.folderNameConflict:
      return "같은 이름의 폴더가 이미 있어요. 이름을 바꿔 주세요.";
    case ORGANIZE_ERROR_CODES.targetChanged:
      return "그사이 지워진 사진이 있어요. 결과를 다시 불러와 주세요.";
    case ORGANIZE_ERROR_CODES.alreadyApplied:
      return "이미 폴더로 만든 결과예요.";
    case ORGANIZE_ERROR_CODES.notReady:
    case ORGANIZE_ERROR_CODES.reviewExpired:
    case ORGANIZE_ERROR_CODES.resultExpired:
      return "확인할 수 있는 시간이 지났어요. 다시 정리해 주세요.";
    default:
      return error.status === 0 ? "네트워크 연결을 확인해 주세요." : APPLY_FALLBACK;
  }
};
