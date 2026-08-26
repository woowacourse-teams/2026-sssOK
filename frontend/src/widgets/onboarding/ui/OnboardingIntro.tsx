import onboardingImage from "@/shared/assets/mascot.png";
import { Description, Highlight, ImageSlot, IntroStack, Title } from "./OnboardingIntro.styles";

export const OnboardingIntro = () => {
  return (
    <IntroStack gap={32} align="center">
      <ImageSlot>
        <img src={onboardingImage} alt="사진을 들고 있는 쏙 캐릭터" />
      </ImageSlot>

      <Title>
        사진 모으고
        <br />
        바로 <Highlight>쏙</Highlight> 나누기
      </Title>

      <Description>
        링크 하나로 사진, 영상을 모으고
        <br />
        필요한 것만 쏙 빼가요.
      </Description>
    </IntroStack>
  );
};
