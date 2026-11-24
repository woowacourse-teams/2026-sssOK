import { useEffect } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { ROUTES } from "@/shared/config";
import { isAppWebView, rememberLoginReturn } from "@/shared/lib";
import { ErrorState, ErrorStateButton } from "@/shared/ui/error-state";
import { PageSpinner } from "@/shared/ui/spinner";

export const RequireAppRoomMember = () =>
  isAppWebView() ? <RequireMember rememberDestination /> : <Outlet />;

export const RequireMember = ({
  rememberDestination = false,
}: {
  rememberDestination?: boolean;
}) => {
  const session = useMemberSessionQuery();

  const { pathname, search, hash } = useLocation();
  const needsLogin = session.isSuccess && !session.data;
  useEffect(() => {
    if (rememberDestination && needsLogin) rememberLoginReturn(pathname + search + hash);
  }, [rememberDestination, needsLogin, pathname, search, hash]);

  if (session.isPending) return <PageSpinner label="로그인 확인 중" />;
  if (session.isError) {
    return (
      <ErrorState
        title="로그인 정보를 확인하지 못했어요."
        description="잠시 후 다시 시도해 주세요."
      >
        <ErrorStateButton
          onClick={() => void session.refetch()}
          disabled={session.isFetching}
          aria-busy={session.isFetching}
        >
          다시 시도
        </ErrorStateButton>
      </ErrorState>
    );
  }
  if (!session.data) return <Navigate to={ROUTES.login} replace />;
  return <Outlet />;
};
