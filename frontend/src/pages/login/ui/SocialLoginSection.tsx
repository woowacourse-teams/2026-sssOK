import { Spinner } from "@/shared/ui/spinner";
import { useSocialLogin } from "../model/useSocialLogin";
import { AppleLoginButton } from "./AppleLoginButton";
import { KakaoLoginButton } from "./KakaoLoginButton";
import { LoginFeedback } from "./LoginFeedback";
import { Actions, LoginControls } from "./LoginPage.styles";

export const SocialLoginSection = () => {
  const { login, isPending, error } = useSocialLogin();

  if (isPending) {
    return (
      <Actions aria-label="소셜 로그인">
        <LoginFeedback error="">
          <LoginControls>
            <Spinner label="로그인 중이에요." delayMs={0} />
          </LoginControls>
        </LoginFeedback>
      </Actions>
    );
  }

  return (
    <Actions aria-label="소셜 로그인">
      <LoginFeedback error={error}>
        <LoginControls>
          <KakaoLoginButton onClick={() => void login("kakao")} />
          <AppleLoginButton onClick={() => void login("apple")} />
        </LoginControls>
      </LoginFeedback>
    </Actions>
  );
};
