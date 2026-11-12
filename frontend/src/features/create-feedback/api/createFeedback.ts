import { z } from "zod";
export const CreateFeedbackResponseSchema = z.object({
  feedbackId: z.number(),
  createdAt: z.string(),
});
import { apiClient } from "@/shared/api";

interface CreateFeedbackParams {
  roomId: number;
  accessToken: string;
  content: string;
}

export type CreateFeedbackResponse = z.infer<typeof CreateFeedbackResponseSchema>;

export const createFeedback = ({
  roomId,
  accessToken,
  content,
}: CreateFeedbackParams): Promise<CreateFeedbackResponse> =>
  apiClient<CreateFeedbackResponse>(`/rooms/${roomId}/feedbacks`, {
    schema: CreateFeedbackResponseSchema,
    method: "POST",
    token: accessToken,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  });
