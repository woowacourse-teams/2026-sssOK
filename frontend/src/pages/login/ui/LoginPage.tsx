import { Link } from "react-router-dom";
import { ROUTES } from "@/shared/config";
import { FaApple } from "react-icons/fa";

import mascot from "@/shared/assets/mascot.png";
import { useAppleLogin } from "../model/useAppleLogin";
import { useKakaoLogin } from "../model/useKakaoLogin";
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
  LegalLinks,
} from "./LoginPage.styles";

export const LoginPage = () => {
  const kakao = useKakaoLogin();
  const apple = useAppleLogin();
  const isPending = kakao.isPending || apple.isPending;

  const handleKakaoLogin = () => {
    if (!isPending) void kakao.login();
  };

  const handleAppleLogin = () => {
    if (!isPending) void apple.login();
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
          <SocialButton
            type="button"
            $provider="kakao"
            disabled={isPending}
            aria-busy={kakao.isPending}
            onClick={handleKakaoLogin}
          >
            <img src={kakaoSymbol} alt="" aria-hidden="true" />
            <span>카카오 로그인</span>
          </SocialButton>
          <SocialButton
            type="button"
            $provider="apple"
            disabled={isPending}
            aria-busy={apple.isPending}
            onClick={handleAppleLogin}
          >
            <FaApple aria-hidden="true" />
            <span>Apple로 로그인</span>
          </SocialButton>
          {(kakao.error || apple.error) && (
            <Notice role="status" aria-live="polite">
              {kakao.error || apple.error}
            </Notice>
          )}
        </Actions>
        <LegalLinks aria-label="약관 및 정책">
          <Link to={ROUTES.terms}>이용약관</Link>
          <Link to={ROUTES.privacy}>개인정보처리방침</Link>
        </LegalLinks>
      </Content>
    </Page>
  );
};
