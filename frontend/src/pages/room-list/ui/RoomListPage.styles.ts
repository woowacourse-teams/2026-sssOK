import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

const DESKTOP = "@media (min-width: 768px)";
const WIDE = "@media (min-width: 1024px)";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
`;

/** 넓은 화면에만 있는 맨 위 막대. 앱(웹뷰)은 이 자리를 셸이 차지한다. */
export const Nav = styled.nav`
  display: none;

  ${DESKTOP} {
    display: block;
    height: 64px;
    border-bottom: 1px solid ${colors.borderDefault};
  }
`;

export const NavInner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1120px;
  height: 100%;
  margin: 0 auto;
  padding: 0 ${spacing[32]};
`;

export const Logo = styled.span`
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
  color: ${colors.textStrong};
  font-size: 20px;
  font-weight: 800;
  line-height: 28px;

  img {
    width: 30px;
    height: 30px;
    border-radius: 9px;
  }
`;

/** 좁은 화면은 맨 위 막대가 없어 알림·프로필 자리를 제목 줄 오른쪽에 둔다. */
export const MobileOnly = styled.div`
  ${DESKTOP} {
    display: none;
  }
`;

export const AccountSlots = styled.div`
  display: flex;
  flex: none;
  align-items: center;
  gap: ${spacing[4]};

  ${DESKTOP} {
    gap: ${spacing[12]};
  }
`;

export const BellSlot = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: ${colors.textStrong};

  svg {
    width: 24px;
    height: 24px;
    /* 굵은 제목 옆에서 얇은 선이 떠 보이지 않게 굵기를 올린다. */
    stroke-width: 2;
  }
`;

export const AvatarSlot = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: ${radius.full};
  background: ${colors.primarySubtle};
  color: ${colors.primary};

  svg {
    width: 16px;
    height: 16px;
  }
`;

export const Body = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  padding: ${spacing[12]} ${spacing[20]} 34px;

  ${DESKTOP} {
    max-width: 1120px;
    margin: 0 auto;
    padding: 40px ${spacing[32]};
  }
`;

export const Header = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${spacing[24]};

  ${DESKTOP} {
    align-items: flex-end;
  }
`;

export const Title = styled.h1`
  color: ${colors.textStrong};
  font-size: 28px;
  font-weight: 800;
  line-height: 36px;
  letter-spacing: -0.3px;

  ${DESKTOP} {
    font-size: 32px;
    line-height: 40px;
    letter-spacing: 0;
  }
`;

export const Description = styled.p`
  margin-top: 2px;
  color: ${colors.textSecondary};
  font-size: 14px;
  line-height: 20px;

  ${DESKTOP} {
    margin-top: ${spacing[4]};
    font-size: 15px;
    line-height: 24px;
  }
`;

/** 넓은 화면에서만 제목 옆에 두는 버튼들. 좁은 화면은 플로팅 버튼이 대신한다. */
export const HeaderActions = styled.div`
  display: none;

  ${DESKTOP} {
    display: flex;
    gap: ${spacing[8]};
  }
`;

export const PageButton = styled.button<{ $variant: "primary" | "line"; $size: "md" | "lg" }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: ${({ $size }) => ($size === "lg" ? "52px" : "44px")};
  padding: 0 ${({ $size }) => ($size === "lg" ? "22px" : "18px")};
  border: ${({ $variant }) => ($variant === "line" ? `1px solid ${colors.borderDefault}` : 0)};
  border-radius: ${({ $size }) => ($size === "lg" ? "14px" : radius[12])};
  background: ${({ $variant }) =>
    $variant === "primary" ? colors.primary : colors.backgroundDefault};
  color: ${({ $variant }) => ($variant === "primary" ? colors.textInverse : colors.textStrong)};
  font-size: ${({ $size }) => ($size === "lg" ? "16px" : "15px")};
  font-weight: 700;
  white-space: nowrap;

  svg {
    width: 18px;
    height: 18px;
  }

  &:active {
    background: ${({ $variant }) =>
      $variant === "primary" ? colors.primaryPressed : colors.interactiveHover};
  }

  @media (hover: hover) {
    &:hover {
      background: ${({ $variant }) =>
        $variant === "primary" ? colors.primaryPressed : colors.interactiveHover};
    }
  }
`;

export const Chips = styled.div`
  display: flex;
  gap: ${spacing[8]};
  margin-top: ${spacing[16]};
  overflow-x: auto;

  ${DESKTOP} {
    margin-top: 28px;
  }
`;

export const Chip = styled.button<{ $selected: boolean }>`
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: ${spacing[4]};
  height: 36px;
  padding: 0 14px;
  border: 1px solid ${({ $selected }) => ($selected ? colors.textStrong : colors.borderDefault)};
  border-radius: ${radius.full};
  background: ${({ $selected }) => ($selected ? colors.textStrong : colors.backgroundDefault)};
  color: ${({ $selected }) => ($selected ? colors.textInverse : colors.textPrimary)};

  ${typography.caption1}

  b {
    font-weight: 700;
    opacity: 0.7;
  }
`;

export const List = styled.ul`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  margin-top: ${spacing[16]};
  /* 마지막 카드가 플로팅 버튼에 가리지 않게 비워 둔다. */
  margin-bottom: 86px;
  list-style: none;

  ${DESKTOP} {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: ${spacing[20]};
    margin-top: ${spacing[20]};
    margin-bottom: 0;
  }

  ${WIDE} {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
`;

export const FilterEmpty = styled.p`
  margin-top: ${spacing[32]};
  color: ${colors.textSecondary};
  text-align: center;

  ${typography.body}
`;

export const Empty = styled.section`
  display: flex;
  flex: 1;
  flex-direction: column;

  ${DESKTOP} {
    flex: none;
    padding-top: 48px;
  }
`;

export const EmptyBody = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${spacing[8]};
  padding: 0 ${spacing[12]};
  text-align: center;

  img {
    width: 200px;
    margin-bottom: ${spacing[8]};
  }

  h2 {
    color: ${colors.textStrong};

    ${typography.heading2}
  }

  p {
    color: ${colors.textSecondary};
    font-size: 15px;
    line-height: 22px;
  }

  ${DESKTOP} {
    flex: none;

    img {
      width: 220px;
    }

    h2 {
      font-size: 22px;
      line-height: 30px;
    }

    p {
      line-height: 24px;
    }

    br {
      display: none;
    }
  }
`;

/** 좁은 화면은 화면 아래에 꽉 차게 쌓고, 넓은 화면은 글 아래에 나란히 둔다. */
export const EmptyActions = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: ${spacing[12]};

  ${DESKTOP} {
    flex-direction: row-reverse;
    justify-content: center;
    gap: ${spacing[8]};
    padding-top: ${spacing[24]};

    button {
      height: 48px;
      border-radius: ${radius[12]};
      font-size: 15px;
    }
  }
`;

export const ErrorBox = styled.section`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${spacing[16]};
  color: ${colors.textSecondary};
  text-align: center;

  ${typography.body}
`;

export const SheetMenuItem = styled.button<{ $tone?: "danger" }>`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
  width: 100%;
  height: 56px;
  padding: 0 ${spacing[4]};
  color: ${({ $tone }) => ($tone === "danger" ? colors.danger : colors.textStrong)};
  font-size: 16px;
  font-weight: 600;
  text-align: left;

  & + & {
    border-top: 1px solid ${colors.backgroundSubtle};
  }

  svg {
    flex: none;
    width: 18px;
    height: 18px;
  }
`;

export const Fab = styled.button`
  position: fixed;
  right: max(${spacing[20]}, calc((100vw - 480px) / 2 + ${spacing[20]}));
  bottom: calc(34px + env(safe-area-inset-bottom));
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 56px;
  padding: 0 22px 0 18px;
  border-radius: ${radius.full};
  background: ${colors.primary};
  color: ${colors.textInverse};
  box-shadow: 0 8px 20px color-mix(in srgb, ${colors.primary} 35%, transparent);

  ${typography.label2}

  svg {
    width: 22px;
    height: 22px;
  }

  ${DESKTOP} {
    display: none;
  }
`;
