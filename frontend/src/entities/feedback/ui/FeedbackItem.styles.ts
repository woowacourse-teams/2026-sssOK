import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

const itemLayout = `
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  padding: ${spacing[20]} ${spacing[16]};
`;

export const Item = styled.li<{ selectable: boolean }>`
  /* 누를 수 있으면 여백을 버튼이 대신 가진다 — 버튼이 칸을 다 채워야 여백을 눌러도 열린다. */
  ${({ selectable }) => (selectable ? "" : itemLayout)}

  border-bottom: 1px solid ${colors.borderDefault};
`;

export const SelectButton = styled.button`
  ${itemLayout}

  width: 100%;
  text-align: left;

  &:hover {
    background: ${colors.interactiveHover};
  }

  &:focus-visible {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: -2px;
  }
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
