import { AdminFeedbackDetailSchema } from "../model/types";
import { apiClient } from "@/shared/api";
import type { AdminFeedbackDetail } from "../model/types";

interface GetAdminFeedbackParams {
  token: string;
  feedbackId: number;
}

export const getAdminFeedback = ({
  token,
  feedbackId,
}: GetAdminFeedbackParams): Promise<AdminFeedbackDetail> =>
  apiClient<AdminFeedbackDetail>(`/admin/feedbacks/${feedbackId}`, {
    schema: AdminFeedbackDetailSchema,
    token,
  });
