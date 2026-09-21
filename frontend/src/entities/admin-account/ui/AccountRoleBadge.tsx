import styled from "@emotion/styled";

import { colors, radius, typography } from "@/shared/styles/tokens";

const Badge = styled.span<{ emphasized: boolean }>`
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding-inline: 10px;
  border-radius: ${radius.full};
  background: ${({ emphasized }) => (emphasized ? colors.primarySubtle : colors.backgroundSubtle)};
  color: ${({ emphasized }) => (emphasized ? colors.textAccent : colors.textSecondary)};
  white-space: nowrap;

  ${typography.caption2}
`;

interface AccountRoleBadgeProps {
  role: string;
}

/**
 * 계정 목록의 역할 칸. 헤더 배지와 달리 역할마다 색을 나눈다 — 목록에서는 누가 슈퍼관리자인지
 * 한눈에 갈려 보여야 한다. 모르는 역할은 헤더와 같이 "관리자" 모양으로 보여준다.
 */
export const AccountRoleBadge = ({ role }: AccountRoleBadgeProps) => {
  const isSuperAdmin = role === "SUPER_ADMIN";

  return <Badge emphasized={isSuperAdmin}>{isSuperAdmin ? "슈퍼관리자" : "관리자"}</Badge>;
};
