import styled from "@emotion/styled";
import { NavLink } from "react-router-dom";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Header = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${spacing[16]};
  min-height: 40px;
  padding: ${spacing[16]} ${spacing[16]} 0;
`;

export const Nav = styled.nav`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: ${spacing[20]};
`;

export const NavItem = styled(NavLink)`
  color: ${colors.textSecondary};

  ${typography.label5}

  &[aria-current="page"] {
    color: ${colors.textStrong};

    ${typography.label4}
  }
`;

/** 갈 화면이 아직 없는 탭. 자리는 보여주되 누를 수 없다. */
export const DisabledNavItem = styled.span`
  color: ${colors.textSecondary};
  cursor: default;

  ${typography.label5}
`;

/**
 * 모바일에서도 이름·역할·로그아웃을 모두 보여준다. 누구로 들어왔는지와 나가는 길은
 * 좁은 화면에서도 둘 다 필요하다. 폭이 모자라면 이름만 줄어든다.
 */
export const Me = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
`;

/** 관리자 이름은 20자까지라 좁은 폰에서는 넘친다. 말줄임하고, 전체 이름은 title 로 남긴다. */
export const Name = styled.span`
  min-width: 0;
  overflow: hidden;
  color: ${colors.textPrimary};
  text-overflow: ellipsis;
  white-space: nowrap;

  ${typography.caption1}
`;

export const RoleBadge = styled.span`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  height: 24px;
  padding-inline: 10px;
  border-radius: ${radius.full};
  background: ${colors.primarySubtle};
  color: ${colors.textAccent};
  white-space: nowrap;

  ${typography.caption2}
`;

export const Divider = styled.span`
  flex-shrink: 0;
  width: 1px;
  height: 12px;
  background: ${colors.borderDefault};
`;

export const LogoutButton = styled.button`
  flex-shrink: 0;
  color: ${colors.textSecondary};
  white-space: nowrap;

  ${typography.caption1}
`;
