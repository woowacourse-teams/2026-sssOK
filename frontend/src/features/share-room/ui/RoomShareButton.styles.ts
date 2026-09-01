import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Anchor = styled.div`
  position: relative;
  display: flex;
  flex: none;
`;

export const Menu = styled.div`
  position: absolute;
  top: calc(100% + ${spacing[16]});
  /* 링크 아이콘 옆의 더보기 버튼까지 포함한 헤더 오른쪽에 맞춘다. */
  right: -36px;
  z-index: 1001;
  width: min(262px, calc(100vw - 32px));
  padding: ${spacing[8]};
  background: ${colors.backgroundDefault};
  border: 1px solid #e5ded1;
  border-radius: ${radius[12]};
  box-shadow: 0 8px 28px rgba(45, 38, 31, 0.16);
`;

export const MenuItem = styled.button`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
  width: 100%;
  min-height: 68px;
  padding: 11px ${spacing[16]};
  border-radius: 10px;
  text-align: left;
  color: ${colors.textStrong};
  background: transparent;

  ${typography.label5}

  svg {
    flex: none;
    width: 20px;
    height: 20px;
  }

  &:hover:not(:disabled),
  &:active:not(:disabled),
  &:focus-visible:not(:disabled) {
    background: ${colors.primarySubtle};
    color: ${colors.primary};
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: -2px;
  }

  &:disabled {
    cursor: not-allowed;
    color: ${colors.textSecondary};
  }
`;

export const Description = styled.span`
  display: block;
  margin-top: 2px;
  color: ${colors.textSecondary};

  ${typography.caption3}
`;
