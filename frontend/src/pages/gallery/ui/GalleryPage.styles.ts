import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  flex-direction: column;
  min-height: 0;
  background-color: ${colors.backgroundDefault};
  overflow: hidden;
`;

export const PageState = styled.main`
  align-items: center;
  justify-content: center;
  padding: ${spacing[24]};
  color: ${colors.textSecondary};

  ${typography.body}
`;

export const FolderEmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[8]};
  text-align: center;
`;

export const FolderEmptyTitle = styled.p`
  color: ${colors.textStrong};

  ${typography.label5}
`;

export const FolderEmptyDescription = styled.p`
  color: ${colors.textSecondary};
  white-space: pre-line;

  ${typography.caption1}
`;
