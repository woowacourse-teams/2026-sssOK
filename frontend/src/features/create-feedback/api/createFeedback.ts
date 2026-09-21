import { apiClient } from "@/shared/api";

interface CreateFeedbackParams {
  roomId: number;
  accessToken: string;
  content: string;
}

export interface CreateFeedbackResponse {
  feedbackId: number;
  createdAt: string;
}

export const createFeedback = ({ roomId, accessToken, content }: CreateFeedbackParams) =>
  apiClient<CreateFeedbackResponse>(`/rooms/${roomId}/feedbacks`, {
    method: "POST",
    token: accessToken,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  });
