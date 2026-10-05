import styled from "@emotion/styled";

import { colors } from "@/shared/styles/tokens";
import { HOME_INTRO_LEAVE_MS } from "../model/useHomeIntro";

/**
 * 홈 위를 덮는 인트로 층. 물러날 때는 배경이 걷히고, 캐릭터는 로고 자리로 줄어들어 로고와 바뀐다.
 * 물러나는 동안에는 누름이 아래 홈으로 그대로 간다.
 */
export const Overlay = styled.div<{ leaving: boolean }>`
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  pointer-events: ${({ leaving }) => (leaving ? "none" : "auto")};
`;

export const Backdrop = styled.div<{ leaving: boolean }>`
  position: absolute;
  inset: 0;
  background-color: ${colors.backgroundDefault};
  opacity: ${({ leaving }) => (leaving ? 0 : 1)};
  transition: opacity ${HOME_INTRO_LEAVE_MS * 0.8}ms ease-out;
`;

/**
 * 캐릭터는 사라지지 않고 로고 자리까지 그대로 줄어든다. 인트로가 끝나는 순간 로고 그림과
 * 한 번에 바뀌므로, 중간에 흐려지면 둘 다 안 보이는 틈이 생겨 깜빡여 보인다.
 * 줄어드는 시간을 물러나는 시간보다 조금 짧게 잡아, 바뀌기 전에 자리에 먼저 닿게 한다.
 */
export const Mascot = styled.img`
  position: relative;
  width: min(320px, 80vw);
  height: auto;
  transition: transform ${HOME_INTRO_LEAVE_MS - 60}ms cubic-bezier(0.65, 0, 0.35, 1);
`;
