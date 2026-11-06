import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

import similarCardMask from "../assets/similar-card-mask.svg";

/* 정리 방식 선택 시트(558:5390)의 카드·썸네일. 시트 틀은 `OrganizeSheet.styles` 에 있다. */

export const Options = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${spacing[8]};
  width: 100%;
  padding: ${spacing[16]} 0;
`;

/** 카드 전체가 라디오 하나다. 숨긴 input 이 선택 상태의 기준이다. */
export const Option = styled.label<{ $disabled: boolean }>`
  display: grid;
  grid-template-rows: 78px 20px;
  row-gap: 6px;
  cursor: ${({ $disabled }) => ($disabled ? "not-allowed" : "pointer")};

  input {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
  }

  &:has(input:focus-visible) > span:first-of-type > span {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: 2px;
  }
`;

/** 시안의 5px 여백. 썸네일이 카드 칸보다 조금 안쪽에 앉는다. */
export const ThumbFrame = styled.span`
  display: block;
  padding: 5px;
`;

export const Thumb = styled.span<{ $selected: boolean }>`
  position: relative;
  display: block;
  height: 68px;
  overflow: hidden;
  border-radius: ${radius[12]};
  background: ${colors.backgroundSubtle};

  /*
   * 고른 카드 테두리. 그림 위에 얹어야 사진에 가려지지 않는다.
   * 칸 크기를 바꾸지 않으려고 border 대신 안쪽 선으로 그린다.
   */
  &::after {
    content: "";
    position: absolute;
    inset: 0;
    border: 2px solid ${({ $selected }) => ($selected ? colors.primary : "transparent")};
    border-radius: inherit;
    pointer-events: none;
    transition: border-color 0.15s ease;
  }
`;

/** 썸네일 그림 한 겹. 준비 중인 방식은 흐리게 해서 아직 고를 수 없다는 걸 보여 준다. */
export const ThumbArt = styled.span<{ $blurred: boolean }>`
  position: absolute;
  inset: 0;
  filter: ${({ $blurred }) => ($blurred ? "blur(3px)" : "none")};
  /* 흐림이 칸 가장자리에서 배경색으로 번지지 않게 조금 키운다. */
  transform: ${({ $blurred }) => ($blurred ? "scale(1.06)" : "none")};
`;

export const Crowd = styled.span`
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[8]};
  padding-top: 12px;
`;

/** 아바타 칸은 34px 이고, 흰 테두리·그림자 몫 2px 씩 바깥으로 넘친다(38px SVG). */
export const Avatar = styled.span`
  position: relative;
  flex-shrink: 0;
  width: 34px;
  height: 34px;

  img {
    position: absolute;
    top: -2px;
    left: -2px;
    display: block;
    /* 전역 img 최대 너비(100%) 때문에 34px 칸에 눌리지 않게 원래 크기(38px)를 지킨다. */
    max-width: none;
  }
`;

/**
 * 풍경 카드 한 장. 흰 카드(66×58) 안에 2px 안쪽으로 풍경(62×54)을 둥근 모서리 마스크로 잘라 얹는다.
 * 가운데 카드 기준으로 좌우 카드가 24px 떨어져 ±8° 기울어 있다.
 */
export const SceneCard = styled.span<{ $offset: -1 | 0 | 1 }>`
  position: absolute;
  top: 5px;
  left: calc(50% - 33px + ${({ $offset }) => $offset * 24}px);
  width: 66px;
  height: 58px;
  transform: rotate(${({ $offset }) => $offset * 8}deg);

  img {
    position: absolute;
    display: block;
    max-width: none;
  }

  img:first-of-type {
    inset: 0;
  }

  img:last-of-type {
    top: 2px;
    left: 2px;
    mask: url(${similarCardMask}) no-repeat 0 0 / 62px 54px;
  }
`;

export const CheckMark = styled.span`
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 11px;
  background: ${colors.primary};

  img {
    display: block;
  }
`;

export const Soon = styled.span`
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 1px 7px;
  border-radius: ${radius.full};
  background: ${colors.backgroundDefault};
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  color: ${colors.textPrimary};
`;

export const Label = styled.span<{ $selected: boolean }>`
  padding-left: 2px;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  color: ${({ $selected }) => ($selected ? colors.primaryPressed : colors.textSecondary)};
`;

/** 로그인하지 않은 사람에게만 보이는 회색 안내. */
export const Notice = styled.p`
  width: 100%;
  margin-bottom: ${spacing[12]};
  color: ${colors.textSecondary};

  ${typography.caption3}
`;
