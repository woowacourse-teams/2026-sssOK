import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

const NARROW = "@media (max-width: 599px)";

const bodyLayout = `
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  padding: ${spacing[20]} ${spacing[16]};
`;

export const Item = styled.li<{ $selectable: boolean }>`
  display: flex;
  align-items: flex-start;
  border-bottom: 1px solid ${colors.borderDefault};

  ${({ $selectable }) =>
    $selectable
      ? `
    &:hover {
      background: ${colors.interactiveHover};
    }
  `
      : ""}

  ${NARROW} {
    flex-direction: column;
  }
`;

export const Body = styled.div`
  ${bodyLayout}

  flex: 1;
  min-width: 0;
`;

export const SelectButton = styled.button`
  ${bodyLayout}

  flex: 1;
  min-width: 0;
  align-self: stretch;
  text-align: left;

  &:focus-visible {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: -2px;
  }

  ${NARROW} {
    width: 100%;
    padding-bottom: ${spacing[12]};
  }
`;

/** 버튼 안에 버튼을 넣을 수 없어 본문 버튼과 형제로 둔다. */
export const Aside = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: ${spacing[8]};
  padding: ${spacing[20]} ${spacing[16]} 0 0;

  ${NARROW} {
    padding: 0 ${spacing[16]} ${spacing[16]};
  }
`;

export const Content = styled.p<{ $settled: boolean }>`
  color: ${({ $settled }) => ($settled ? colors.textSecondary : colors.textStrong)};
  overflow-wrap: anywhere;

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
