import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import type { AdminFeedback, AdminFeedbackDetail, AdminFeedbackPage } from "./types";
import { adminFeedbackQueryKey } from "./useAdminFeedbackQuery";
import { adminFeedbacksQueryKey } from "./useAdminFeedbacksQuery";

export type FeedbackTriage = Pick<AdminFeedback, "status" | "assignee">;

type FeedbackPages = InfiniteData<AdminFeedbackPage>;

export const findFeedbackInPages = (data: FeedbackPages | undefined, feedbackId: number) => {
  for (const page of data?.pages ?? []) {
    const found = page.feedbacks.find((feedback) => feedback.feedbackId === feedbackId);

    if (found) return found;
  }

  return undefined;
};

const patchPages = (
  data: FeedbackPages | undefined,
  feedbackId: number,
  triage: Partial<FeedbackTriage>,
): FeedbackPages | undefined => {
  if (!data) return data;

  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      feedbacks: page.feedbacks.map((feedback) =>
        feedback.feedbackId === feedbackId ? { ...feedback, ...triage } : feedback,
      ),
    })),
  };
};

export const patchFeedbackInList = (
  queryClient: QueryClient,
  feedbackId: number,
  triage: Partial<FeedbackTriage>,
) => {
  queryClient.setQueryData<FeedbackPages>(adminFeedbacksQueryKey, (data) =>
    patchPages(data, feedbackId, triage),
  );
};

/**
 * 응답의 `content` 는 전문이라 목록에는 상태·담당자만 옮긴다.
 * 상세는 `staleTime: Infinity` 라 여기서 맞추지 않으면 바뀌기 전 값이 남는다.
 */
export const applyChangedFeedback = (queryClient: QueryClient, feedback: AdminFeedbackDetail) => {
  patchFeedbackInList(queryClient, feedback.feedbackId, {
    status: feedback.status,
    assignee: feedback.assignee,
  });
  queryClient.setQueryData(adminFeedbackQueryKey(feedback.feedbackId), feedback);
};
