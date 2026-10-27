import { Navigate, Outlet } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { ROUTES } from "@/shared/config";
import { PageSpinner } from "@/shared/ui/spinner";

/** 이미 로그인한 회원은 로그인 화면 대신 방 목록으로 보낸다. */
export const RedirectIfMember = () => {
  const session = useMemberSessionQuery();

  if (session.isPending) return <PageSpinner label="로그인 확인 중" />;
  if (session.data && !session.isError) return <Navigate to={ROUTES.rooms} replace />;
  return <Outlet />;
};
