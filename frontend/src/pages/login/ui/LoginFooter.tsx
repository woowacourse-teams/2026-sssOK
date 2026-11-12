import { Link } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { LegalLinks } from "./LoginPage.styles";

export const LoginFooter = () => (
  <LegalLinks aria-label="약관 및 정책">
    <Link to={ROUTES.terms}>이용약관</Link>
    <Link to={ROUTES.privacy}>개인정보처리방침</Link>
  </LegalLinks>
);
