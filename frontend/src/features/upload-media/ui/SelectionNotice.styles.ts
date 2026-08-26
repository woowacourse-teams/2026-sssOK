import styled from "@emotion/styled";

import { colors, radius, shadow, spacing, typography } from "@/shared/styles/tokens";

export const Notice = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${spacing[8]};
  width: 100%;
  max-width: 350px;
  padding: ${spacing[12]} ${spacing[16]};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[16]};
  background-color: ${colors.backgroundDefault};
  box-shadow: ${shadow.toast};
`;

export const NoticeBody = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: ${spacing[4]};
  min-width: 0;
`;

export const SelectedCount = styled.p`
  color: ${colors.textStrong};

  ${typography.label4}
`;

export const RejectedCount = styled.p`
  color: ${colors.danger};

  ${typography.label4}
`;

export const ReasonList = styled.ul`
  display: flex;
  flex-direction: column;
  gap: ${spacing[4]};
  color: ${colors.textSecondary};
  list-style: none;

  ${typography.caption1}
`;

export const DismissButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  background: none;
  color: ${colors.textSecondary};
  cursor: pointer;

  > svg {
    width: 18px;
    height: 18px;
  }
`;
