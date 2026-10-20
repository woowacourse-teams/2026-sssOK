import styled from "@emotion/styled";
import type { ReactNode } from "react";

import { colors, radius, spacing } from "@/shared/styles/tokens";
import { feedbackAssigneeLabel, feedbackStatusLabel } from "../lib/feedbackTriage";
import type { FeedbackAssignee, FeedbackStatus } from "../model/types";

const STATUS_COLORS: Record<FeedbackStatus, { background: string; color: string }> = {
  UNREFLECTED: { background: colors.warningSubtle, color: colors.warning },
  IN_PROGRESS: { background: colors.infoSubtle, color: colors.info },
  REFLECTED: { background: colors.successSubtle, color: colors.success },
  ON_HOLD: { background: colors.backgroundSubtle, color: colors.textSecondary },
};

const Pill = styled.span`
  display: inline-flex;
  align-items: center;
  gap: ${spacing[4]};
  height: 27px;
  padding-inline: ${spacing[12]};
  border-radius: ${radius.full};
  white-space: nowrap;
  /* 같은 크기의 토큰이 아직 없다. */
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;

  svg {
    width: 13px;
    height: 13px;
  }
`;

const StatusPill = styled(Pill)<{ $status: FeedbackStatus }>`
  background: ${({ $status }) => STATUS_COLORS[$status]?.background ?? colors.backgroundSubtle};
  color: ${({ $status }) => STATUS_COLORS[$status]?.color ?? colors.textSecondary};
`;

const AssigneePill = styled(Pill)<{ $empty: boolean }>`
  padding-inline: 11px;
  border: 1px ${({ $empty }) => ($empty ? "dashed" : "solid")} ${colors.borderDefault};
  background: ${colors.backgroundDefault};
  color: ${({ $empty }) => ($empty ? colors.textSecondary : colors.textPrimary)};
`;

export const FeedbackBadgeButton = styled.button`
  display: inline-flex;
  border-radius: ${radius.full};

  &:hover > span {
    filter: brightness(0.97);
  }

  &:focus-visible {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: 2px;
  }
`;

interface BadgeProps {
  trailing?: ReactNode;
}

export const FeedbackStatusBadge = ({
  status,
  trailing,
}: BadgeProps & { status: FeedbackStatus }) => (
  <StatusPill $status={status}>
    {feedbackStatusLabel(status)}
    {trailing}
  </StatusPill>
);

export const FeedbackAssigneeBadge = ({
  assignee,
  trailing,
}: BadgeProps & { assignee: FeedbackAssignee | null }) => (
  <AssigneePill $empty={assignee === null}>
    {feedbackAssigneeLabel(assignee)}
    {trailing}
  </AssigneePill>
);
