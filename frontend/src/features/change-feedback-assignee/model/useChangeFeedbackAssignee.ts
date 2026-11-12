import { useFeedbackTriageMutation } from "@/entities/feedback";
import { changeFeedbackAssignee } from "../api/changeFeedbackAssignee";

export const useChangeFeedbackAssignee = (
  token: string,
): ReturnType<typeof useFeedbackTriageMutation<"assignee">> =>
  useFeedbackTriageMutation("assignee", ({ feedbackId, value }) =>
    changeFeedbackAssignee({ token, feedbackId, assignee: value }),
  );
