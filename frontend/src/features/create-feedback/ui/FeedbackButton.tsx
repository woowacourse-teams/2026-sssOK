import { LuMessageCircleMore } from "react-icons/lu";

import { Dock, FloatingFeedbackButton } from "./FeedbackButton.styles";

interface FeedbackButtonProps {
  hidden?: boolean;
  onClick?: () => void;
}

export const FeedbackButton = ({ hidden = false, onClick }: FeedbackButtonProps) => {
  if (hidden) return null;

  return (
    <Dock>
      <FloatingFeedbackButton type="button" aria-label="문의하기" onClick={onClick}>
        <LuMessageCircleMore aria-hidden="true" />
      </FloatingFeedbackButton>
    </Dock>
  );
};
