import { useEffect } from "react";
import { Navigate } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { ROUTES } from "@/shared/config";
import { isAppWebView, leaveToLanding } from "@/shared/lib";
import { PageSpinner } from "@/shared/ui/spinner";

/** 홈(/home)·알 수 없는 주소로 들어오면 로그인한 사람은 방 목록, 웹 게스트는 랜딩으로 보낸다. */
export const StartRedirect = () => {
  const session = useMemberSessionQuery();
  const toRooms = isAppWebView() || !!session.data;
  const toLanding = !toRooms && !session.isPending;

  useEffect(() => {
    if (toLanding) leaveToLanding();
  }, [toLanding]);

  if (toRooms) return <Navigate to={ROUTES.rooms} replace />;
  return <PageSpinner label="이동하는 중" />;
};
