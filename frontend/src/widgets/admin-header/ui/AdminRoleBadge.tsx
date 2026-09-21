import { RoleBadge } from "./AdminHeader.styles";

interface AdminRoleBadgeProps {
  role: string;
}

/**
 * 역할을 사람이 읽는 이름으로 보여준다.
 *
 * `SUPER_ADMIN` 만 따로 강조하고 나머지는 전부 "관리자" 모양이다. 서버에 역할이 늘어도
 * 모르는 값에서 화면이 비거나 원문(`AUDITOR` 같은)이 그대로 찍히지 않게 한다.
 */
export const AdminRoleBadge = ({ role }: AdminRoleBadgeProps) => {
  const isSuperAdmin = role === "SUPER_ADMIN";

  return <RoleBadge emphasized={isSuperAdmin}>{isSuperAdmin ? "슈퍼관리자" : "관리자"}</RoleBadge>;
};
