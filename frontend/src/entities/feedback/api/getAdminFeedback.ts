import { apiClient } from "@/shared/api";
import type { AdminFeedbackDetail } from "../model/types";

interface GetAdminFeedbackParams {
  token: string;
  feedbackId: number;
}

export const getAdminFeedback = ({ token, feedbackId }: GetAdminFeedbackParams) =>
  apiClient<AdminFeedbackDetail>(`/admin/feedbacks/${feedbackId}`, { token });
