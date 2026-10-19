import { useFeedbackTriageMutation } from "@/entities/feedback";
import { changeFeedbackAssignee } from "../api/changeFeedbackAssignee";

export const useChangeFeedbackAssignee = (token: string) =>
  useFeedbackTriageMutation("assignee", ({ feedbackId, value }) =>
    changeFeedbackAssignee({ token, feedbackId, assignee: value }),
  );
