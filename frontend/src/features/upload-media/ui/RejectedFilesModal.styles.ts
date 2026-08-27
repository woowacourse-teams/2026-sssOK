import styled from "@emotion/styled";

import { Button } from "@/shared/ui/button";
import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[20]};
`;

export const Head = styled.div`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
`;

/** 시안(07d)의 경고 배지. 제목 왼쪽에 붙어 무슨 종류의 알림인지 한눈에 말한다. */
export const WarnBadge = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: ${radius[12]};
  background: ${colors.primarySubtle};
  color: ${colors.warning};

  svg {
    width: 20px;
    height: 20px;
  }
`;

export const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;

/**
 * 걸러진 파일이 많으면 목록이 화면을 넘긴다. 목록만 스크롤시키고
 * 제목과 확인 버튼은 제자리에 둔다 — 나갈 길이 스크롤 밖으로 밀리면 안 된다.
 */
export const FileList = styled.ul`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  max-height: 180px;
  overflow-y: auto;
`;

export const FileRow = styled.li`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
`;

/** 어떤 파일인지 가리키는 자리. 시안은 회색 판이라 원본을 읽지 않는다 — 2GB 짜리도 있다. */
export const Thumb = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: ${radius[12]};
  background: ${colors.borderDefault};
  color: ${colors.textSecondary};

  svg {
    width: 18px;
    height: 18px;
  }
`;

export const FileTexts = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[4]};
  min-width: 0;
`;

/** 파일명이 길어도 줄을 늘리지 않는다. 30장을 걸렀을 때 목록이 화면을 덮는다. */
export const FileName = styled.span`
  overflow: hidden;
  color: ${colors.textStrong};
  text-overflow: ellipsis;
  white-space: nowrap;

  ${typography.caption2}
`;

export const Reason = styled.span`
  color: ${colors.warning};

  ${typography.caption3}
`;

export const Limits = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${spacing[8]};
`;

/** 한도는 파일마다 반복하지 않고 여기서 한 번만 말한다. */
export const LimitChip = styled.span`
  display: flex;
  align-items: center;
  gap: ${spacing[4]};
  padding: ${spacing[8]} ${spacing[12]};
  border-radius: ${radius.full};
  background: ${colors.interactiveHover};
  color: ${colors.textStrong};

  ${typography.caption4}
`;

export const ConfirmAction = styled(Button)`
  width: 100%;
`;
