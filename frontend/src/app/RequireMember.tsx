import styled from "@emotion/styled";
import { Navigate, Outlet } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { ROUTES } from "@/shared/config";
import { spacing } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";
import { PageSpinner } from "@/shared/ui/spinner";

export const RequireMember = () => {
  const session = useMemberSessionQuery();

  if (session.isPending) return <PageSpinner label="로그인 확인 중" />;
  if (session.isError) {
    return (
      <State role="alert">
        <p>로그인 정보를 확인하지 못했어요.</p>
        <Button onClick={() => void session.refetch()} disabled={session.isFetching}>
          다시 시도
        </Button>
      </State>
    );
  }
  if (!session.data) return <Navigate to={ROUTES.login} replace />;
  return <Outlet />;
};

const State = styled.main({
  minHeight: "100svh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: spacing[16],
  padding: spacing[24],
});
