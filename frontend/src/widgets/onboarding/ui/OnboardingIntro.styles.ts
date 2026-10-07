import styled from "@emotion/styled";

import { colors, typography } from "@/shared/styles/tokens";
import { Stack } from "@/shared/ui/stack";

export const IntroStack = styled(Stack)`
  width: 100%;
`;

export const Logo = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: ${colors.textAccent};
  ${typography.heading2};

  /*
   * 반복 재생되는 인사 SVG 는 사진이 날아갈 여백까지 품고 있어 캐릭터가 작게 보인다.
   * 그림을 키우고 위아래 여백은 겹쳐, 헤드라인 줄 높이는 그대로 둔다.
   */
  img {
    width: 56px;
    height: auto;
    margin: -10px -2px -8px -6px;
  }

  /* 넓은 화면에서는 헤드라인(40px)이 커지는 만큼 로고 글자도 키운다 */
  @media (min-width: 768px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

/**
 * heading1(32px) 이면 375px 폭에서 첫 줄이 넘친다. 두 줄로 끊기는 크기로 줄여
 * 헤드라인·미리보기·버튼이 한 화면에 들어오게 한다.
 */
export const Title = styled.h1`
  color: ${colors.textStrong};
  ${typography.heading1};
  font-size: 24px;
  line-height: 32px;
  letter-spacing: -0.01em;

  /* 넓은 화면에서는 왼쪽 단을 헤드라인이 이끈다 */
  @media (min-width: 768px) {
    font-size: 40px;
    line-height: 52px;
  }
`;

export const Highlight = styled.span`
  color: ${colors.textAccent};
`;
