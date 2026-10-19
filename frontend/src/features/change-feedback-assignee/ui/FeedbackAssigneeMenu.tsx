import { useState } from "react";
import { HiChevronDown } from "react-icons/hi2";

import {
  FEEDBACK_ASSIGNEES,
  FeedbackAssigneeBadge,
  FeedbackBadgeButton,
  UNASSIGNED_LABEL,
  feedbackAssigneeLabel,
  type AdminFeedback,
  type FeedbackAssignee,
} from "@/entities/feedback";
import { withDirectionParticle } from "@/shared/lib";
import {
  DropdownMenu,
  DropdownMenuAnchor,
  DropdownMenuCheck,
  DropdownMenuDivider,
  DropdownMenuItem,
} from "@/shared/ui/dropdown-menu";
import { useChangeFeedbackAssignee } from "../model/useChangeFeedbackAssignee";

interface FeedbackAssigneeMenuProps {
  token: string;
  feedback: Pick<AdminFeedback, "feedbackId" | "assignee">;
  onChanged: (message: string) => void;
  onFailed: (error: unknown) => void;
}

export const FeedbackAssigneeMenu = ({
  token,
  feedback,
  onChanged,
  onFailed,
}: FeedbackAssigneeMenuProps) => {
  const [open, setOpen] = useState(false);
  const { mutate } = useChangeFeedbackAssignee(token);

  const choose = (assignee: FeedbackAssignee | null) => {
    setOpen(false);

    if (assignee === feedback.assignee) return;

    mutate(
      { feedbackId: feedback.feedbackId, value: assignee },
      {
        onSuccess: () =>
          onChanged(
            assignee === null
              ? `의견 #${feedback.feedbackId} 담당자를 비웠어요.`
              : `의견 #${feedback.feedbackId} 담당자를 ${withDirectionParticle(`${feedbackAssigneeLabel(assignee)}님`)} 정했어요.`,
          ),
        onError: onFailed,
      },
    );
  };

  const item = (value: FeedbackAssignee | null, label: string) => (
    <DropdownMenuItem
      key={value ?? "none"}
      role="menuitemradio"
      aria-checked={value === feedback.assignee}
      icon={<DropdownMenuCheck checked={value === feedback.assignee} />}
      onClick={() => choose(value)}
    >
      {label}
    </DropdownMenuItem>
  );

  return (
    <DropdownMenuAnchor>
      <FeedbackBadgeButton
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`담당자 바꾸기, 지금 ${feedbackAssigneeLabel(feedback.assignee)}`}
        onClick={() => setOpen((current) => !current)}
      >
        <FeedbackAssigneeBadge
          assignee={feedback.assignee}
          trailing={<HiChevronDown aria-hidden="true" />}
        />
      </FeedbackBadgeButton>

      {open && (
        <DropdownMenu onClose={() => setOpen(false)}>
          <div role="menu" aria-label="담당자">
            {FEEDBACK_ASSIGNEES.map(({ value, label }) => item(value, label))}
            <DropdownMenuDivider />
            {item(null, UNASSIGNED_LABEL)}
          </div>
        </DropdownMenu>
      )}
    </DropdownMenuAnchor>
  );
};
