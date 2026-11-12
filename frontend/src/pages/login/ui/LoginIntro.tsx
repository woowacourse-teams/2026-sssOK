import mascot from "@/shared/assets/mascot.png";
import {
  Intro,
  Illustration,
  IntroText,
  Heading,
  Highlight,
  Description,
} from "./LoginPage.styles";

export const LoginIntro = () => (
  <Intro>
    <Illustration src={mascot} alt="" />
    <IntroText>
      <Heading>
        사진 모으고
        <br />
        바로 <Highlight>쏙</Highlight> 나누기
      </Heading>
      <Description>
        링크 하나로 사진, 영상을 모으고
        <br />
        필요한 것만 쏙 빼가요.
      </Description>
    </IntroText>
  </Intro>
);
