import { useEffect, useRef } from "react";
import { Outlet, useNavigate } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { ROUTES } from "@/shared/config";
import { takeLoginReturn } from "@/shared/lib";
import { PageSpinner } from "@/shared/ui/spinner";

export const RedirectIfMember = () => {
  const session = useMemberSessionQuery();
  const navigate = useNavigate();
  const redirected = useRef(false);
  const isMember = !!session.data && !session.isError;

  useEffect(() => {
    if (isMember && !redirected.current) {
      redirected.current = true;
      navigate(takeLoginReturn() ?? ROUTES.rooms, { replace: true });
    }
  }, [isMember, navigate]);

  if (session.isPending || isMember) return <PageSpinner label="로그인 확인 중" />;
  return <Outlet />;
};
