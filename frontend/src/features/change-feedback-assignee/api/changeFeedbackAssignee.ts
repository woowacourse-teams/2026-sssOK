import { AdminFeedbackDetailSchema } from "../../../entities/feedback/model/types";
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
}: ChangeFeedbackAssigneeParams): Promise<AdminFeedbackDetail> =>
  apiClient<AdminFeedbackDetail>(`/admin/feedbacks/${feedbackId}/assignee`, {
    schema: AdminFeedbackDetailSchema,
    method: "PATCH",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ assignee }),
  });
