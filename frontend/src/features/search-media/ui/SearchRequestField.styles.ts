import styled from "@emotion/styled";

import { colors, radius, shadow, spacing, typography } from "@/shared/styles/tokens";

export const Wrap = styled.div`
  position: relative;
  width: 100%;
`;

/** 패널이 열리거나 결과를 보는 동안에는 테두리를 강조색으로 바꾼다. */
export const Bar = styled.form<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
  height: 48px;
  padding: 0 ${spacing[4]} 0 ${spacing[16]};
  border: 1.5px solid ${({ $active }) => ($active ? colors.borderPrimary : colors.borderDefault)};
  border-radius: ${radius[12]};

  background-color: ${colors.backgroundDefault};
`;

export const BarIcon = styled.span<{ $active: boolean }>`
  display: inline-flex;
  flex: none;
  color: ${({ $active }) => ($active ? colors.primary : colors.textSecondary)};

  > svg {
    width: 20px;
    height: 20px;
  }
`;

export const Input = styled.input`
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  outline: none;

  background: transparent;
  color: ${colors.textStrong};

  ${typography.body}

  &::placeholder {
    color: ${colors.textSecondary};
  }

  /* 브라우저 기본 지우기 버튼은 닫기 버튼과 겹친다. */
  &::-webkit-search-cancel-button {
    appearance: none;
  }
`;

export const IconAction = styled.button`
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: ${radius.full};

  background: transparent;
  color: ${colors.textSecondary};
  cursor: pointer;

  > svg {
    width: 20px;
    height: 20px;
  }
`;

export const Panel = styled.div`
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  left: 0;
  z-index: 10;
  padding: ${spacing[12]} 14px 6px;
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[16]};

  background-color: ${colors.backgroundDefault};
  box-shadow: ${shadow.modal};
`;

export const PanelTitle = styled.p`
  margin: 0 0 ${spacing[4]};
  color: ${colors.textSecondary};

  ${typography.caption2}
`;

export const ModeHead = styled.div`
  display: flex;
  align-items: center;
  gap: 2px;
  margin: -6px 0 2px -10px;

  > p {
    margin: 0;
    color: ${colors.textStrong};

    ${typography.caption1}
  }
`;

export const Note = styled.p`
  margin: 0;
  padding: 6px 0 10px;
  color: ${colors.textPrimary};

  ${typography.caption1}
  font-weight: 500;
`;

export const Item = styled.button`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
  width: 100%;
  min-height: 56px;
  padding: ${spacing[8]} 0;
  border: 0;

  background: transparent;
  text-align: left;
  cursor: pointer;

  & + & {
    border-top: 1px solid ${colors.backgroundSubtle};
  }

  > span:nth-of-type(2) {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: ${colors.textStrong};
    text-overflow: ellipsis;
    white-space: nowrap;

    ${typography.label5}
  }

  small {
    color: ${colors.textSecondary};

    ${typography.caption3}
  }

  /* 정리 중이라 막힌 줄. 까닭(small)은 읽혀야 하므로 줄 전체를 흐리게 하지 않는다. */
  &:disabled {
    cursor: not-allowed;

    strong,
    > span:first-of-type,
    > span:last-of-type {
      opacity: 0.45;
    }
  }
`;

export const Glyph = styled.span`
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: ${radius[12]};

  background-color: ${colors.backgroundSubtle};
  color: ${colors.textPrimary};

  > svg {
    width: 18px;
    height: 18px;
  }
`;

export const Arrow = styled.span`
  display: inline-flex;
  flex: none;
  color: ${colors.textSecondary};

  > svg {
    width: 18px;
    height: 18px;
  }
`;
