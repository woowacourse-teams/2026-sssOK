import { useQuery } from "@tanstack/react-query";

import { isApiError } from "@/shared/api";
import { getAdminFeedback } from "../api/getAdminFeedback";

/**
 * 목록 키(`["admin", "feedbacks"]`) 아래에 두지 않는다. 목록을 무효화할 때 열어 둔 상세까지
 * 같이 다시 부르게 되는데, 의견은 쓰고 나면 바뀌지 않아서 그럴 이유가 없다.
 */
export const adminFeedbackQueryKey = (feedbackId: number) =>
  ["admin", "feedback", feedbackId] as const;

/** token 을 queryKey 에 넣지 않는 이유는 `useAdminFeedbacksQuery` 와 같다. */
export const useAdminFeedbackQuery = (token: string, feedbackId: number) =>
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  useQuery({
    queryKey: adminFeedbackQueryKey(feedbackId),
    queryFn: () => getAdminFeedback({ token, feedbackId }),
    // 한 번 쓴 의견은 고칠 수 없다. 같은 의견을 다시 열 때 또 부를 이유가 없다.
    staleTime: Infinity,
    // 없는 의견·권한 없음은 다시 불러도 답이 같다. 서버·네트워크 실패만 한 번 더 부른다.
    retry: (failureCount, error) => !(isApiError(error) && error.status < 500) && failureCount < 1,
  });
