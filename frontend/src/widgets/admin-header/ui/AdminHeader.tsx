import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { type AdminSession, removeAdminSession } from "@/entities/admin-session";
import { ROUTES } from "@/shared/config";
import {
  DisabledNavItem,
  Divider,
  Header,
  LogoutButton,
  Me,
  Name,
  Nav,
  NavItem,
} from "./AdminHeader.styles";
import { AdminRoleBadge } from "./AdminRoleBadge";

interface AdminHeaderProps {
  session: AdminSession;
}

/**
 * 관리자 화면 공통 헤더. 어느 화면인지, 누구로 들어왔는지, 어떻게 나가는지를 보여준다.
 *
 * 이름·역할은 로그인 응답을 저장해 둔 세션에서 읽는다. 서버에 "나" 를 묻는 API 가 없고,
 * 헤더 하나 그리자고 요청을 더 보낼 이유도 없다.
 */
export const AdminHeader = ({ session }: AdminHeaderProps) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  /*
   * 서버에 로그아웃 API 가 없다 — 토큰은 만료될 때까지 서버에서 유효하다.
   * 여기서 할 수 있는 건 이 브라우저가 토큰을 잊는 것까지다.
   *
   * 캐시도 같이 버린다. 남겨 두면 다른 관리자로 다시 들어왔을 때 앞 사람이 본 목록이 먼저 비친다.
   */
  const logout = () => {
    removeAdminSession();
    queryClient.removeQueries({ queryKey: ["admin"] });
    navigate(ROUTES.adminLogin, { replace: true });
  };

  return (
    <Header>
      <Nav aria-label="관리자 메뉴">
        <NavItem to={ROUTES.adminFeedbacks}>의견</NavItem>
        {/* 계정 화면은 아직 없다. 계정 화면 이슈에서 링크로 바꾼다. */}
        <DisabledNavItem aria-disabled="true">계정</DisabledNavItem>
      </Nav>

      <Me>
        <Name title={session.name}>{session.name}</Name>
        <AdminRoleBadge role={session.role} />
        <Divider aria-hidden="true" />
        <LogoutButton type="button" onClick={logout}>
          로그아웃
        </LogoutButton>
      </Me>
    </Header>
  );
};
