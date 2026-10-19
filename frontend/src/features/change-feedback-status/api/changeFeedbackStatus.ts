import type { AdminFeedbackDetail, FeedbackStatus } from "@/entities/feedback";
import { apiClient } from "@/shared/api";

interface ChangeFeedbackStatusParams {
  token: string;
  feedbackId: number;
  status: FeedbackStatus;
}

export const changeFeedbackStatus = ({ token, feedbackId, status }: ChangeFeedbackStatusParams) =>
  apiClient<AdminFeedbackDetail>(`/admin/feedbacks/${feedbackId}/status`, {
    method: "PATCH",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
