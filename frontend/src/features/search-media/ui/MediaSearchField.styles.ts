import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { DropdownMenuItem } from "@/shared/ui/dropdown-menu";

/** 열고 닫는 데 걸리는 시간. 닫을 때는 이만큼 기다린 뒤 검색창을 내린다. */
export const SEARCH_FIELD_MOTION_MS = 280;

const HIDDEN = `clip-path: inset(0 0 0 100% round ${radius[12]}); opacity: 0;`;
const SHOWN = `clip-path: inset(0 0 0 0 round ${radius[12]}); opacity: 1;`;

const revealFromRight = keyframes`
  from {
    ${HIDDEN}
  }

  to {
    ${SHOWN}
  }
`;

const concealToRight = keyframes`
  from {
    ${SHOWN}
  }

  to {
    ${HIDDEN}
  }
`;

/** 방 제목 줄과 같은 높이를 지켜 헤더 아래가 밀리지 않게 한다. */
export const SearchForm = styled.form<{ $closing: boolean }>`
  display: flex;
  flex: 1;
  align-items: center;
  gap: ${spacing[8]};
  min-width: 0;
  height: 40px;
  padding: 0 ${spacing[4]} 0 ${spacing[12]};
  border: 1.5px solid ${colors.borderPrimary};
  border-radius: ${radius[12]};

  background-color: ${colors.backgroundDefault};

  animation: ${({ $closing }) => ($closing ? concealToRight : revealFromRight)}
    ${SEARCH_FIELD_MOTION_MS}ms
    ${({ $closing }) =>
      $closing ? "cubic-bezier(0.32, 0, 0.67, 0)" : "cubic-bezier(0.22, 1, 0.36, 1)"}
    /* 열린 뒤에도 clip-path 가 남으면 아래로 펼치는 검색 기준 메뉴가 잘린다. 닫을 때만 끝 상태를 유지한다. */
    ${({ $closing }) => ($closing ? "forwards" : "none")};

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
`;

export const SearchTypeContainer = styled.div`
  position: relative;
  display: inline-flex;
  flex: none;
`;

export const SearchTypeButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 28px;
  padding: 0 ${spacing[4]} 0 ${spacing[8]};
  border: 0;
  border-radius: ${radius[12]};

  background-color: ${colors.primarySubtle};
  color: ${colors.primaryPressed};
  cursor: pointer;

  ${typography.caption1}
  font-weight: 700;

  > svg {
    width: 14px;
    height: 14px;
  }

  &:focus-visible {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: 1px;
  }
`;

/** 공용 메뉴 항목에 설명 줄과 오른쪽 체크를 더한다. 고른 기준은 강조색으로 보인다. */
export const SearchTypeOption = styled(DropdownMenuItem)`
  > span {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: space-between;
    gap: ${spacing[12]};
  }

  > span > span {
    display: flex;
    flex-direction: column;
  }

  strong {
    ${typography.label5}
  }

  small {
    color: ${colors.textSecondary};

    ${typography.caption3}
  }

  &[aria-checked="true"] {
    color: ${colors.primaryPressed};
  }

  svg {
    width: 18px;
    height: 18px;
  }
`;

export const SearchTypeDivider = styled.span`
  flex: none;
  width: 1px;
  height: 16px;

  background-color: ${colors.borderDefault};
`;

export const SearchInput = styled.input`
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

export const CloseButton = styled.button`
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: ${radius.full};

  background: transparent;
  color: ${colors.textSecondary};
  cursor: pointer;

  > svg {
    width: 18px;
    height: 18px;
  }
`;
