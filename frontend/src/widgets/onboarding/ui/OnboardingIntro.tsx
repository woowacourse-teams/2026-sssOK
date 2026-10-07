import mascotHello from "@/shared/assets/mascot-hello.svg";
import { Highlight, IntroStack, Logo, Title } from "./OnboardingIntro.styles";

export const OnboardingIntro = () => {
  return (
    <IntroStack gap={12}>
      <Logo>
        {/* 구멍에서 나와 인사하는 캐릭터가 계속 반복된다. 움직임을 줄이는 설정이면 SVG 가 스스로 멈춘다 */}
        <img src={mascotHello} alt="" />쏙
      </Logo>

      <Title>
        링크로 모으고,
        <br />
        <Highlight>쏙</Highlight> 골라 받기!
      </Title>
    </IntroStack>
  );
};
