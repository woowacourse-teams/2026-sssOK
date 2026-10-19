import { useMemo, useState } from "react";

import {
  FEEDBACK_STATUSES,
  sortFeedbacksByStatus,
  type AdminFeedback,
  type FeedbackAssignee,
  type FeedbackStatus,
} from "@/entities/feedback";

/** `null` 은 담당자가 없는 의견만 고른다. */
export type AssigneeFilter = "ALL" | FeedbackAssignee | null;

export type StatusTab = "ALL" | FeedbackStatus;

/** 서버에 필터·정렬 파라미터가 없어 이미 불러온 페이지 안에서만 거르고 센다. */
export const useTriageFilter = (feedbacks: AdminFeedback[]) => {
  const [statusTab, setStatusTab] = useState<StatusTab>("ALL");
  const [assigneeFilter, setAssigneeFilter] = useState<AssigneeFilter>("ALL");

  const byAssignee = useMemo(
    () =>
      assigneeFilter === "ALL"
        ? feedbacks
        : feedbacks.filter(({ assignee }) => assignee === assigneeFilter),
    [feedbacks, assigneeFilter],
  );

  const counts = useMemo(
    () =>
      Object.fromEntries([
        ["ALL", byAssignee.length],
        ...FEEDBACK_STATUSES.map(({ value }) => [
          value,
          byAssignee.filter(({ status }) => status === value).length,
        ]),
      ]) as Record<StatusTab, number>,
    [byAssignee],
  );

  const visibleFeedbacks = useMemo(
    () =>
      sortFeedbacksByStatus(
        statusTab === "ALL" ? byAssignee : byAssignee.filter(({ status }) => status === statusTab),
      ),
    [byAssignee, statusTab],
  );

  return {
    statusTab,
    setStatusTab,
    assigneeFilter,
    setAssigneeFilter,
    counts,
    visibleFeedbacks,
  };
};
