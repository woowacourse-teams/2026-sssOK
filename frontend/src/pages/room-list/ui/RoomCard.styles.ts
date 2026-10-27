import styled from "@emotion/styled";
import { Link } from "react-router-dom";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

const DESKTOP = "@media (min-width: 768px)";

export const Card = styled.li`
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: ${spacing[12]};
  border: 1px solid ${colors.borderDefault};
  border-radius: 18px;
  background: ${colors.backgroundDefault};

  ${DESKTOP} {
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 0;
    overflow: hidden;
    transition:
      box-shadow 0.15s,
      transform 0.15s;

    &:hover,
    &:focus-within {
      box-shadow: 0 10px 24px rgba(43, 35, 30, 0.1);
      transform: translateY(-2px);
    }

    &:hover [data-menu-slot],
    &:focus-within [data-menu-slot] {
      opacity: 1;
    }
  }
`;

export const Cover = styled.div`
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  overflow: hidden;
  border-radius: ${radius[12]};
  background: ${colors.backgroundSubtle};
  color: ${colors.borderDisabled};

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  svg {
    width: 24px;
    height: 24px;
  }

  ${DESKTOP} {
    width: 100%;
    height: auto;
    aspect-ratio: 4 / 3;
    border-radius: 0;

    svg {
      width: 40px;
      height: 40px;
    }
  }
`;

export const Info = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: ${spacing[4]};
  min-width: 0;

  ${DESKTOP} {
    gap: 6px;
    padding: 14px ${spacing[16]} ${spacing[16]};
  }
`;

/** 카드 전체를 누를 수 있게 늘린다. 메뉴 버튼은 이 위에 따로 올라간다. */
export const NameLink = styled(Link)`
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: ${colors.textStrong};
  text-decoration: none;

  ${typography.label2}

  &::after {
    content: "";
    position: absolute;
    inset: 0;
    border-radius: inherit;
  }

  &:focus-visible {
    outline: none;
  }

  &:focus-visible::after {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: 2px;
  }
`;

export const Name = styled.span`
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const HostBadge = styled.span`
  display: inline-flex;
  flex: none;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 6px;
  background: ${colors.backgroundSubtle};
  color: ${colors.textPrimary};
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
`;

export const Meta = styled.p`
  color: ${colors.textSecondary};
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
`;

export const MenuSlot = styled.div`
  position: relative;
  z-index: 1;
  flex: none;

  ${DESKTOP} {
    position: absolute;
    top: 10px;
    right: 10px;

    /* 마우스가 있을 때만 숨겼다가 카드에 올리면 꺼낸다. 터치 태블릿은 올릴 수가 없어 늘 보인다. */
    @media (hover: hover) {
      opacity: 0;
      transition: opacity 0.15s;
    }
  }
`;

export const MoreButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: ${colors.textSecondary};

  svg {
    width: 20px;
    height: 20px;
  }

  ${DESKTOP} {
    width: 32px;
    height: 32px;
    background: rgba(255, 255, 255, 0.92);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
    color: ${colors.textStrong};

    svg {
      width: 18px;
      height: 18px;
    }
  }
`;
