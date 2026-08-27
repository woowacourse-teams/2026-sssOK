import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

/**
 * 목록이 길어지면 상자만 스크롤한다. 버튼이 스크롤 밖으로 밀려나면
 * 나갈 길을 찾으려고 스크롤을 내려야 한다.
 */
export const ListBox = styled.ul`
  max-height: 132px;
  padding: 0 ${spacing[12]};
  border-radius: ${radius[12]};
  background: ${colors.interactiveHover};
  overflow-y: auto;
`;

export const Row = styled.li`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${spacing[12]};
  padding: ${spacing[12]} 0;

  /* 구분선은 줄 사이에만 그린다. 마지막 줄 아래에 그으면 상자 테두리처럼 보인다. */
  & + & {
    border-top: 1px solid ${colors.borderDefault};
  }
`;

/** 파일명이 길어도 줄을 늘리지 않는다. 꼬리표가 밀려나면 사유를 못 읽는다. */
export const RowName = styled.span`
  overflow: hidden;
  min-width: 0;
  color: ${colors.textStrong};
  text-overflow: ellipsis;
  white-space: nowrap;

  ${typography.caption2}
`;

export const Reason = styled.span`
  flex-shrink: 0;
  color: ${colors.warning};

  ${typography.caption4}
`;
