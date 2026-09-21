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

  @media (min-width: 600px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  @media (min-width: 860px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  @media (min-width: 1080px) {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
`;

export const StateMessage = styled.div`
  display: grid;
  flex: 1;
  place-items: center;
  padding: ${spacing[32]} ${spacing[16]};
  color: ${colors.textSecondary};

  ${typography.body}
`;

export const Sentinel = styled.div`
  height: 1px;
`;

export const LoadingMore = styled.p`
  padding: ${spacing[16]};
  color: ${colors.textSecondary};
  text-align: center;

  ${typography.caption3}
`;
