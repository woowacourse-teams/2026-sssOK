import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

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

const shimmer = keyframes`
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
`;

export const SkeletonCard = styled.div`
  width: 100%;
  aspect-ratio: 8 / 9;
  border-radius: ${radius[12]};
  background: linear-gradient(
    90deg,
    ${colors.backgroundSubtle} 25%,
    ${colors.interactiveHover} 50%,
    ${colors.backgroundSubtle} 75%
  );
  background-size: 200% 100%;
  animation: ${shimmer} 1.4s ease-in-out infinite;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
`;

export const HiddenLabel = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  border: 0;
  clip: rect(0 0 0 0);
  white-space: nowrap;
`;
