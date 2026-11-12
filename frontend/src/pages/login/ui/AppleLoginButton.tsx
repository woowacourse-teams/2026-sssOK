import styled from "@emotion/styled";
import { FaApple } from "react-icons/fa";

import { SocialButton } from "./LoginPage.styles";

interface AppleLoginButtonProps {
  onClick: () => void;
}

export const AppleLoginButton = ({ onClick }: AppleLoginButtonProps) => (
  <AppleButton type="button" onClick={onClick}>
    <FaApple aria-hidden="true" />
    <span>Apple로 로그인</span>
  </AppleButton>
);

const AppleButton = styled(SocialButton)({
  background: "#000000",
  color: "#FFFFFF",
});
