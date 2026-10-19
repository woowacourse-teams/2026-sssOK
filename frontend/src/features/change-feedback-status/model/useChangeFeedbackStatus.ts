import { useFeedbackTriageMutation } from "@/entities/feedback";
import { changeFeedbackStatus } from "../api/changeFeedbackStatus";

export const useChangeFeedbackStatus = (token: string) =>
  useFeedbackTriageMutation("status", ({ feedbackId, value }) =>
    changeFeedbackStatus({ token, feedbackId, status: value }),
  );
