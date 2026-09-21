import { RoleBadge } from "./AdminHeader.styles";

interface AdminRoleBadgeProps {
  role: string;
}

/**
 * 역할을 사람이 읽는 이름으로 보여준다. 모양은 역할과 상관없이 같고 이름만 다르다.
 *
 * `SUPER_ADMIN` 이 아니면 전부 "관리자" 다. 서버에 역할이 늘어도 모르는 값에서
 * 화면이 비거나 원문(`AUDITOR` 같은)이 그대로 찍히지 않게 한다.
 */
export const AdminRoleBadge = ({ role }: AdminRoleBadgeProps) => (
  <RoleBadge>{role === "SUPER_ADMIN" ? "슈퍼관리자" : "관리자"}</RoleBadge>
);
