import type { AdminFeedbackDetail, FeedbackAssignee } from "@/entities/feedback";
import { apiClient } from "@/shared/api";

interface ChangeFeedbackAssigneeParams {
  token: string;
  feedbackId: number;
  assignee: FeedbackAssignee | null;
}

export const changeFeedbackAssignee = ({
  token,
  feedbackId,
  assignee,
}: ChangeFeedbackAssigneeParams) =>
  apiClient<AdminFeedbackDetail>(`/admin/feedbacks/${feedbackId}/assignee`, {
    method: "PATCH",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ assignee }),
  });
