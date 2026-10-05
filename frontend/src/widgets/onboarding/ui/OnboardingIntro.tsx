import logoImage from "@/shared/assets/mascot-wave.png";
import { Highlight, IntroStack, Logo, Title } from "./OnboardingIntro.styles";

export const OnboardingIntro = () => {
  return (
    <IntroStack gap={12}>
      <Logo>
        <img src={logoImage} alt="" />쏙
      </Logo>

      <Title>
        링크 하나로 사진 모으고,
        <br />
        원하는 것만 <Highlight>쏙</Highlight> 다운 받기!
      </Title>
    </IntroStack>
  );
};
