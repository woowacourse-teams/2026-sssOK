import { useMutation, useQueryClient, type InfiniteData } from "@tanstack/react-query";

import {
  applyChangedFeedback,
  findFeedbackInPages,
  patchFeedbackInList,
  type FeedbackTriage,
} from "./feedbackCache";
import type { AdminFeedbackDetail, AdminFeedbackPage } from "./types";
import { adminFeedbacksQueryKey } from "./useAdminFeedbacksQuery";

interface TriageVariables<K extends keyof FeedbackTriage> {
  feedbackId: number;
  value: FeedbackTriage[K];
}

/** 상태·담당자 변경의 공통 흐름. 목록에 먼저 반영하고 실패하면 되돌린다. */
export const useFeedbackTriageMutation = <K extends keyof FeedbackTriage>(
  field: K,
  request: (variables: TriageVariables<K>) => Promise<AdminFeedbackDetail>,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: request,
    onMutate: async ({ feedbackId, value }) => {
      // 늦게 도착한 목록 응답이 낙관적 값을 덮지 않게 한다.
      await queryClient.cancelQueries({ queryKey: adminFeedbacksQueryKey, exact: true });

      const previous = findFeedbackInPages(
        queryClient.getQueryData<InfiniteData<AdminFeedbackPage>>(adminFeedbacksQueryKey),
        feedbackId,
      );

      patchFeedbackInList(queryClient, feedbackId, { [field]: value });

      return { previous: previous ? { [field]: previous[field] } : undefined };
    },
    onError: (_error, { feedbackId }, context) => {
      if (context?.previous) patchFeedbackInList(queryClient, feedbackId, context.previous);
    },
    onSuccess: (feedback) => applyChangedFeedback(queryClient, feedback),
  });
};
