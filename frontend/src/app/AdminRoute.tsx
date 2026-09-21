import { Navigate, Outlet } from "react-router-dom";

import { readValidAdminSession } from "@/entities/admin-session";
import { ROUTES } from "@/shared/config";

/**
 * 관리자 세션이 없으면 로그인 화면으로 보낸다.
 *
 * 저장소를 렌더링 중에 동기로 읽는다 — 방 세션(`readValidRoomSession`)과 같은 방식이다.
 * 한 프레임이라도 관리자 화면이 비쳤다가 튕기는 것보다, 처음부터 그리지 않는 편이 낫다.
 *
 * 이건 화면을 가리는 문일 뿐 보안 경계가 아니다. 실제 판정은 서버가 토큰을 보고 한다.
 */
export const AdminRoute = () => {
  if (!readValidAdminSession()) {
    return <Navigate to={ROUTES.adminLogin} replace />;
  }

  return <Outlet />;
};
