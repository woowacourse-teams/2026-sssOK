import styled from "@emotion/styled";
import { Link } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { colors, spacing } from "@/shared/styles/tokens";

interface LoginCallbackErrorProps {
  message: string;
}

export const LoginCallbackError = ({ message }: LoginCallbackErrorProps) => (
  <Page>
    <h1>로그인을 완료하지 못했어요</h1>
    <p role="alert">{message}</p>
    <Link to={ROUTES.login} replace>
      로그인 다시 시작하기
    </Link>
  </Page>
);

const Page = styled.main({
  minHeight: "100svh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: spacing[16],
  padding: spacing[24],
  textAlign: "center",
  "& a": { color: colors.primary, textDecoration: "underline" },
});
