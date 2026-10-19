import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";
import { NARROW } from "./responsive";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  padding-block: ${spacing[24]};
`;

export const Title = styled.h1`
  padding-inline: ${spacing[16]};
  color: ${colors.textStrong};

  ${typography.heading2}
`;

export const List = styled.ul`
  display: flex;
  flex-direction: column;
`;

/** 목록 끝. 화면에 들어오면 다음 페이지를 부른다 — 보이는 것은 없다. */
export const Sentinel = styled.div`
  height: 1px;
`;

export const Notice = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${spacing[12]};
  padding: ${spacing[24]} ${spacing[16]};
  color: ${colors.textSecondary};

  ${typography.body}
`;

export const LoadingMore = styled.p`
  padding: ${spacing[16]};
  color: ${colors.textSecondary};
  text-align: center;

  ${typography.caption3}
`;

export const Filters = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: ${spacing[12]} ${spacing[24]};
  padding: ${spacing[20]} ${spacing[16]} ${spacing[12]};

  ${NARROW} {
    flex-direction: column;
    gap: 10px;
    padding-inline: 0;
  }
`;

export const FilterLabel = styled.span`
  margin-right: ${spacing[4]};
  color: ${colors.textSecondary};

  ${typography.caption2}

  font-weight: 500;
`;
