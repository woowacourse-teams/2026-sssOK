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

export const Nav = styled.nav`
  padding-block: ${spacing[12]};

  ${DESKTOP} {
    border-bottom: 1px solid ${colors.borderDefault};
  }
`;

export const NavInner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 ${spacing[20]};

  ${DESKTOP} {
    padding: 0 ${spacing[32]};
  }
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
    width: 26px;
    height: 26px;
    border-radius: 9px;
  }

  ${DESKTOP} {
    img {
      width: 30px;
      height: 30px;
    }
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
    width: ${spacing[24]};
    height: ${spacing[24]};
  }
`;

export const Body = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: ${spacing[16]};
  width: 100%;
  padding: ${spacing[12]} ${spacing[20]} 34px;

  ${DESKTOP} {
    max-width: 1120px;
    gap: ${spacing[24]};
    margin: 0 auto;
    padding: 40px ${spacing[32]};
  }
`;

export const Header = styled.header`
  display: contents;

  ${DESKTOP} {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: ${spacing[24]};
  }
`;

export const HeaderIntro = styled.div`
  display: none;

  ${DESKTOP} {
    display: flex;
    flex-direction: column;
    gap: ${spacing[4]};
  }
`;

export const Title = styled.h1`
  color: ${colors.textStrong};
  font-size: 28px;
  font-weight: 800;
  line-height: 36px;
  letter-spacing: -0.3px;

  ${DESKTOP} {
    ${typography.heading1}
    letter-spacing: 0;
  }
`;

export const SectionDescription = styled.p`
  color: ${colors.textSecondary};
  ${typography.caption1}
`;

export const HeaderActions = styled.div`
  display: flex;
  gap: ${spacing[8]};

  > button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: ${spacing[4]};
    white-space: nowrap;

    svg {
      width: 18px;
      height: 18px;
    }
  }

  @media (max-width: 767px) {
    position: fixed;
    inset-inline: 0;
    bottom: 0;
    max-width: 480px;
    margin-inline: auto;
    z-index: 10;
    padding: ${spacing[12]} ${spacing[20]} calc(${spacing[20]} + env(safe-area-inset-bottom, 0px));
    background: ${colors.backgroundDefault};

    > button {
      flex: 1;
      min-width: 0;
      padding-inline: ${spacing[4]};
    }
  }

  ${DESKTOP} {
    > button {
      width: auto;
      height: 44px;
      padding-inline: 18px;
      border-radius: ${radius[12]};
      ${typography.label4}
    }
  }
`;

export const Chips = styled.div`
  display: flex;
  gap: ${spacing[8]};
  overflow-x: auto;
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

export const RoomListStack = styled.div<{ $isEmpty: boolean }>`
  display: flex;
  flex-direction: column;
  gap: ${({ $isEmpty }) => ($isEmpty ? spacing[32] : spacing[16])};

  ${DESKTOP} {
    gap: ${({ $isEmpty }) => ($isEmpty ? spacing[32] : spacing[20])};
  }
`;

export const List = styled.ul`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  /* 마지막 카드가 플로팅 버튼에 가리지 않게 비워 둔다. */
  margin-bottom: calc(86px + env(safe-area-inset-bottom, 0px));
  list-style: none;

  ${DESKTOP} {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: ${spacing[20]};
    margin-bottom: 0;
  }

  ${WIDE} {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
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
