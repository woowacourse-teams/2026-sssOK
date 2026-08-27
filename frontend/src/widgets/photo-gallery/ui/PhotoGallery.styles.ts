import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const GallerySection = styled.section`
  flex: 1;
  min-height: 0;
  width: 100%;
  padding: ${spacing[20]} ${spacing[16]} 120px ${spacing[16]};
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
`;

export const GalleryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${spacing[12]};
`;

export const StateMessage = styled.div`
  display: grid;
  flex: 1;
  place-items: center;
  padding: ${spacing[32]} ${spacing[16]};
  color: ${colors.textSecondary};

  ${typography.body}
`;
