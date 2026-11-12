import styled from "@emotion/styled";

import kakaoSymbol from "../assets/kakao-symbol.svg";
import { SocialButton } from "./LoginPage.styles";

interface KakaoLoginButtonProps {
  onClick: () => void;
}

export const KakaoLoginButton = ({ onClick }: KakaoLoginButtonProps) => (
  <KakaoButton type="button" onClick={onClick}>
    <img src={kakaoSymbol} alt="" aria-hidden="true" />
    <span>카카오 로그인</span>
  </KakaoButton>
);

const KakaoButton = styled(SocialButton)({
  background: "#FEE500",
  color: "rgba(0, 0, 0, 0.85)",
});
