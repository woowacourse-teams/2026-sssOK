import { useState } from "react";
import { FaApple } from "react-icons/fa";

import mascot from "@/shared/assets/mascot.png";
import kakaoSymbol from "../assets/kakao-symbol.svg";
import {
  Page,
  Content,
  Intro,
  Illustration,
  IntroText,
  Heading,
  Highlight,
  Description,
  Actions,
  SocialButton,
  Notice,
} from "./LoginPage.styles";

export const LoginPage = () => {
  const [notice, setNotice] = useState("");

  const handleLogin = (provider: string) => {
    setNotice(`${provider} 로그인을 준비하고 있어요.`);
  };

  return (
    <Page>
      <Content>
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
        <Actions aria-label="소셜 로그인">
          <SocialButton type="button" $provider="kakao" onClick={() => handleLogin("카카오")}>
            <img src={kakaoSymbol} alt="" aria-hidden="true" />
            <span>카카오 로그인</span>
          </SocialButton>
          <SocialButton type="button" $provider="apple" onClick={() => handleLogin("Apple")}>
            <FaApple aria-hidden="true" />
            <span>Apple로 로그인</span>
          </SocialButton>
          {notice && (
            <Notice role="status" aria-live="polite">
              {notice}
            </Notice>
          )}
        </Actions>
      </Content>
    </Page>
  );
};
