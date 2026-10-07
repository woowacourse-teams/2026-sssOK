import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

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
    forwards;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }

  > svg {
    flex: none;
    width: 20px;
    height: 20px;
    color: ${colors.primaryPressed};
  }
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
