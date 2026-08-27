import styled from "@emotion/styled";

import { Button } from "@/shared/ui/button";
import { colors, spacing, typography } from "@/shared/styles/tokens";

/**
 * 가운데 정렬하지 않는다 (시안 07d · 07g). 부제가 두 줄이라 가운데로 모으면
 * 짧은 둘째 줄이 안쪽으로 들어가 두 줄의 시작점이 어긋난다 — 왼쪽으로 붙여야 한 문단으로 읽힌다.
 */
export const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[16]};
`;

export const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.label2}
`;

/**
 * 왜 그랬는지와 무엇을 하면 되는지를 여기서 한 번만 말한다.
 * 파일마다 반복하면 목록이 문장으로 덮여 정작 파일명이 안 읽힌다.
 */
export const Description = styled.p`
  margin-top: -${spacing[8]};
  color: ${colors.textSecondary};

  ${typography.caption3}
`;

export const Actions = styled.div`
  display: flex;
  gap: ${spacing[8]};
  width: 100%;
  margin-top: ${spacing[4]};
`;

/**
 * 시안에서 두 버튼의 폭이 다르다 — 권하는 쪽이 조금 넓다.
 * 닫기 46 : 재시도 54 의 비율을 그대로 가져온다.
 *
 * 배경도 흰색이 아니라 크림색이다. 공용 `Button` 의 `default` 는 흰 배경이라
 * 여기서만 덮어쓴다 — 다른 화면의 버튼까지 같이 바뀌면 안 된다.
 */
export const CloseAction = styled(Button)`
  flex: 46;
  background-color: ${colors.interactiveHover};
`;

export const PrimaryAction = styled(Button)`
  flex: 54;
`;
