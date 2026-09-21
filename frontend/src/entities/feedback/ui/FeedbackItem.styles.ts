import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Item = styled.li`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  padding: ${spacing[20]} ${spacing[16]};
  border-bottom: 1px solid ${colors.borderDefault};
`;

export const Content = styled.p`
  color: ${colors.textStrong};

  ${typography.body}
`;

export const Meta = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${spacing[8]};
`;

export const MetaText = styled.span`
  color: ${colors.textSecondary};

  ${typography.caption3}
`;
