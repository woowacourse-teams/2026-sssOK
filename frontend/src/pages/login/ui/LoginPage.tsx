import { LoginIntro } from "./LoginIntro";
import { SocialLoginSection } from "./SocialLoginSection";
import { LoginFooter } from "./LoginFooter";
import { Page } from "./LoginPage.styles";

export const LoginPage = () => (
  <Page>
    <LoginIntro />
    <SocialLoginSection />
    <LoginFooter />
  </Page>
);
