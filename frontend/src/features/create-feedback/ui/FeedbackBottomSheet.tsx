import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";

import { isApiError } from "@/shared/api";
import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { Button } from "@/shared/ui/button";
import { Stack } from "@/shared/ui/stack";
import { Textarea } from "@/shared/ui/textarea";
import { createFeedback } from "../api/createFeedback";

interface FeedbackBottomSheetProps {
  roomId: number;
  accessToken: string;
  onClose: () => void;
  onSuccess: () => void;
}

const MAX_FEEDBACK_LENGTH = 300;

export const FeedbackBottomSheet = ({
  roomId,
  accessToken,
  onClose,
  onSuccess,
}: FeedbackBottomSheetProps) => {
  const [content, setContent] = useState("");
  const mutation = useMutation({
    mutationFn: (feedbackContent: string) =>
      createFeedback({ roomId, accessToken, content: feedbackContent }),
    onSuccess: () => {
      onClose();
      onSuccess();
    },
  });

  const trimmedContent = content.trim();
  const errorMessage = mutation.isError
    ? isApiError(mutation.error)
      ? mutation.error.message
      : "문의를 보내지 못했어요. 잠시 후 다시 시도해 주세요."
    : "";

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!trimmedContent || mutation.isPending) return;
    mutation.mutate(trimmedContent);
  };

  return (
    <BottomSheet title="불편한 점을 알려주세요!" onClose={mutation.isPending ? undefined : onClose}>
      <form onSubmit={submit}>
        <Stack gap={16}>
          <Textarea
            autoFocus
            label="문의 내용"
            placeholder="예) 업로드 속도가 너무 느려요"
            value={content}
            maxLength={MAX_FEEDBACK_LENGTH}
            errorMessage={errorMessage}
            disabled={mutation.isPending}
            onValueChange={(value) => {
              setContent(value);
              if (mutation.isError) mutation.reset();
            }}
          />
          <Button type="submit" disabled={!trimmedContent || mutation.isPending}>
            {mutation.isPending ? "보내는 중..." : "문의하기"}
          </Button>
        </Stack>
      </form>
    </BottomSheet>
  );
};
