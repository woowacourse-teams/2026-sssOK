import styled from "@emotion/styled";

import { Button } from "@/shared/ui/button";
import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[12]};
  text-align: center;
`;

/**
 * 시안의 표시 크기다. 지금 에셋이 딱 이 폭(106px)이라 1배수로 들어가 고해상도 화면에서는
 * 흐리게 보인다 — 3배수나 SVG 로 갈아끼우면 여기는 그대로 두면 된다.
 */
export const Mascot = styled.img`
  width: 106px;
  height: auto;
`;

export const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;

export const Description = styled.p`
  color: ${colors.textSecondary};

  ${typography.caption1}
`;

const Action = styled(Button)`
  height: 55px;
`;

export const Actions = styled.div`
  display: flex;
  gap: ${spacing[12]};
  width: 100%;
  margin-top: ${spacing[4]};
`;

/**
 * 시안의 닫기는 흰색이 아니라 크림색이다. 공용 `Button` 의 `default` 는 흰 배경이라
 * 여기서만 덮어쓴다 — 다른 화면의 버튼까지 같이 바뀌면 안 된다.
 */
export const CloseAction = styled(Action)`
  flex: 1;
  background-color: ${colors.interactiveHover};
`;

/** 권하는 쪽이라 시안에서도 더 넓다. */
export const RetryAction = styled(Action)`
  flex: 1.4;
`;
