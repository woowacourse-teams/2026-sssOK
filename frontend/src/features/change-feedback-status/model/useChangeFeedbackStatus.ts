import { useFeedbackTriageMutation } from "@/entities/feedback";
import { changeFeedbackStatus } from "../api/changeFeedbackStatus";

export const useChangeFeedbackStatus = (
  token: string,
): ReturnType<typeof useFeedbackTriageMutation<"status">> =>
  useFeedbackTriageMutation("status", ({ feedbackId, value }) =>
    changeFeedbackStatus({ token, feedbackId, status: value }),
  );
