import { useState } from "react";
import { HiChevronDown } from "react-icons/hi2";

import {
  FEEDBACK_STATUSES,
  FeedbackBadgeButton,
  FeedbackStatusBadge,
  feedbackStatusLabel,
  type AdminFeedback,
  type FeedbackStatus,
} from "@/entities/feedback";
import { withDirectionParticle } from "@/shared/lib";
import {
  DropdownMenu,
  DropdownMenuAnchor,
  DropdownMenuCheck,
  DropdownMenuItem,
} from "@/shared/ui/dropdown-menu";
import { useChangeFeedbackStatus } from "../model/useChangeFeedbackStatus";

interface FeedbackStatusMenuProps {
  token: string;
  feedback: Pick<AdminFeedback, "feedbackId" | "status">;
  onChanged: (message: string) => void;
  onFailed: (error: unknown) => void;
}

export const FeedbackStatusMenu = ({
  token,
  feedback,
  onChanged,
  onFailed,
}: FeedbackStatusMenuProps) => {
  const [open, setOpen] = useState(false);
  const { mutate } = useChangeFeedbackStatus(token);

  const choose = (status: FeedbackStatus) => {
    setOpen(false);

    if (status === feedback.status) return;

    mutate(
      { feedbackId: feedback.feedbackId, value: status },
      {
        onSuccess: () =>
          onChanged(
            `의견 #${feedback.feedbackId} 상태를 ${withDirectionParticle(feedbackStatusLabel(status))} 바꿨어요.`,
          ),
        onError: onFailed,
      },
    );
  };

  return (
    <DropdownMenuAnchor>
      <FeedbackBadgeButton
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`처리 상태 바꾸기, 지금 ${feedbackStatusLabel(feedback.status)}`}
        onClick={() => setOpen((current) => !current)}
      >
        <FeedbackStatusBadge
          status={feedback.status}
          trailing={<HiChevronDown aria-hidden="true" />}
        />
      </FeedbackBadgeButton>

      {open && (
        <DropdownMenu onClose={() => setOpen(false)}>
          <div role="menu" aria-label="처리 상태">
            {FEEDBACK_STATUSES.map(({ value, label }) => (
              <DropdownMenuItem
                key={value}
                role="menuitemradio"
                aria-checked={value === feedback.status}
                icon={<DropdownMenuCheck checked={value === feedback.status} />}
                onClick={() => choose(value)}
              >
                {label}
              </DropdownMenuItem>
            ))}
          </div>
        </DropdownMenu>
      )}
    </DropdownMenuAnchor>
  );
};
