import type { RefObject } from "react";

import logoImage from "@/shared/assets/mascot-hello-still.svg";
import { Highlight, IntroStack, Logo, Title } from "./OnboardingIntro.styles";

interface OnboardingIntroProps {
  /** 인트로 캐릭터가 줄어들며 들어올 자리 */
  logoRef?: RefObject<HTMLImageElement | null>;
  /** 인트로 캐릭터가 아직 화면에 있으면 로고 그림을 숨겨 두 마리가 겹쳐 보이지 않게 한다 */
  hideLogo?: boolean;
}

export const OnboardingIntro = ({ logoRef, hideLogo = false }: OnboardingIntroProps) => {
  return (
    <IntroStack gap={12}>
      <Logo>
        <img
          ref={logoRef}
          src={logoImage}
          alt=""
          style={{ visibility: hideLogo ? "hidden" : undefined }}
        />
        쏙
      </Logo>

      <Title>
        링크 하나로 사진 모으고,
        <br />
        원하는 것만 <Highlight>쏙</Highlight> 다운 받기!
      </Title>
    </IntroStack>
  );
};
