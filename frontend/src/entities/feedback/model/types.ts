/** 관리자 의견 목록의 한 건 (`GET /admin/feedbacks`). */
export interface AdminFeedback {
  feedbackId: number;
  /**
   * 목록에서는 서버가 100자에서 잘라 `...` 을 붙여 보낸다.
   * 프론트에서 다시 자르지 않는다 — 두 곳에서 자르면 기준이 어긋난다.
   */
  content: string;
  roomId: number;
  /**
   * 작성 시점의 복사본이다. 방이 이미 영구 삭제됐어도 값이 남는다.
   * 그래서 `roomId` 로 방을 다시 조회하면 없을 수 있다 — 살아 있는 방으로 가는 링크로 쓰면 안 된다.
   */
  roomName: string;
  memberId: number;
  /** `feedback.nickname` 은 NULL 을 허용한다 (`V19`) — 회원이 이미 정리된 뒤일 수 있다. */
  nickname: string | null;
  /** 해석하지 않은 원본. 헤더 없이 들어온 의견(curl·봇)은 null 이다. */
  userAgent: string | null;
  /** 프론트가 `X-App-Version` 으로 실어 보낸 태그. 예: `fe-v0.1.0`. 안 보내면 null 이다. */
  frontendVersion: string | null;
  /** ISO 8601 (UTC). */
  createdAt: string;
}

/**
 * 관리자 의견 단건 (`GET /admin/feedbacks/{feedbackId}`).
 *
 * 모양은 목록 한 건과 같지만 `content` 가 **자르지 않은 전문**이다.
 * 이름을 따로 두는 건, 목록에서 받은 값을 상세 자리에 그대로 넘기는 실수를 타입에서 드러내려는 것이다.
 */
export type AdminFeedbackDetail = AdminFeedback;

export interface AdminFeedbackPage {
  /** 최신순 (`createdAt` 내림차순, 같은 시각은 `feedbackId` 내림차순). */
  feedbacks: AdminFeedback[];
  /** 다음 페이지 커서. 더 없으면 null. */
  nextCursor: number | null;
  hasNext: boolean;
}
