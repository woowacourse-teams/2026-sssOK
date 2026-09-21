import styled from "@emotion/styled";
import { NavLink } from "react-router-dom";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

/** 이 폭부터 이름·역할을 함께 보여준다. `.container` 가 넓어지는 기준과 같다. */
const DESKTOP = "@media (min-width: 768px)";

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

export const Me = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`;

/** 모바일에서는 이름·역할을 숨기고 로그아웃만 남긴다. 좁은 폭에서 탭과 부딪힌다. */
const desktopOnly = `
  display: none;

  ${DESKTOP} {
    display: flex;
  }
`;

export const Name = styled.span`
  ${desktopOnly}

  color: ${colors.textPrimary};

  ${typography.caption1}
`;

export const RoleBadge = styled.span`
  ${desktopOnly}

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
  ${desktopOnly}

  width: 1px;
  height: 12px;
  background: ${colors.borderDefault};
`;

export const LogoutButton = styled.button`
  color: ${colors.textSecondary};
  white-space: nowrap;

  ${typography.caption1}
`;
