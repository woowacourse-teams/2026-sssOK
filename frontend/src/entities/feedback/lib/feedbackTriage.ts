import type { AdminFeedback, FeedbackAssignee, FeedbackStatus } from "../model/types";

export const FEEDBACK_STATUSES: readonly { value: FeedbackStatus; label: string }[] = [
  { value: "UNREFLECTED", label: "미반영" },
  { value: "IN_PROGRESS", label: "처리중" },
  { value: "REFLECTED", label: "반영" },
  { value: "ON_HOLD", label: "보류" },
];

export const FEEDBACK_ASSIGNEES: readonly { value: FeedbackAssignee; label: string }[] = [
  { value: "MAICHAN", label: "마이찬" },
  { value: "HAENI", label: "해니" },
  { value: "YUNDOL", label: "윤돌" },
  { value: "HYUNMIBAP", label: "현미밥" },
];

export const UNASSIGNED_LABEL = "담당 없음";

export const feedbackStatusLabel = (status: FeedbackStatus) =>
  FEEDBACK_STATUSES.find(({ value }) => value === status)?.label ?? status;

export const feedbackAssigneeLabel = (assignee: FeedbackAssignee | null) =>
  assignee === null
    ? UNASSIGNED_LABEL
    : (FEEDBACK_ASSIGNEES.find(({ value }) => value === assignee)?.label ?? assignee);

export const isFeedbackSettled = ({ status }: Pick<AdminFeedback, "status">) =>
  status === "REFLECTED" || status === "ON_HOLD";

/** 목록 정렬 순서. 탭 순서와 다르다. */
const SORT_ORDER: readonly FeedbackStatus[] = [
  "IN_PROGRESS",
  "UNREFLECTED",
  "REFLECTED",
  "ON_HOLD",
];

const statusRank = (status: FeedbackStatus) => {
  const index = SORT_ORDER.indexOf(status);

  return index === -1 ? SORT_ORDER.length : index;
};

/** 서버에 정렬 파라미터가 없어 이미 불러온 페이지 안에서만 정렬한다. */
export const sortFeedbacksByStatus = (feedbacks: AdminFeedback[]): AdminFeedback[] =>
  feedbacks
    .map((feedback, index) => ({ feedback, index }))
    .sort(
      (a, b) => statusRank(a.feedback.status) - statusRank(b.feedback.status) || a.index - b.index,
    )
    .map(({ feedback }) => feedback);
