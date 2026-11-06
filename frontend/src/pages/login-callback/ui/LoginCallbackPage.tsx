import styled from "@emotion/styled";
import { Link, Navigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { colors, spacing } from "@/shared/styles/tokens";
import { PageSpinner } from "@/shared/ui/spinner";
import { useCompleteLogin } from "../model/useCompleteLogin";

export const LoginCallbackPage = () => {
  const { status, error, returnTo } = useCompleteLogin();

  if (status === "pending") return <PageSpinner label="로그인 완료 중" />;
  // 방에서 로그인하러 왔으면 그 방으로, 아니면 방 목록으로 간다.
  if (status === "success") return <Navigate to={returnTo ?? ROUTES.rooms} replace />;
  return (
    <Page>
      <h1>로그인을 완료하지 못했어요</h1>
      <p role="alert">{error}</p>
      <Link to={ROUTES.login} replace>
        로그인 다시 시작하기
      </Link>
    </Page>
  );
};

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
