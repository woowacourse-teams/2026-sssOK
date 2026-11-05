import styled from "@emotion/styled";

import { colors, radius } from "@/shared/styles/tokens";

/*
 * 사람별 나누기 검토 화면. 피그마 "AI-2 · 이름 붙이기 전"(563:87), "AI-3 · 이름 붙인 후"(564:87),
 * "AI-3c · 같은 사람 합친 뒤"(570:87), "AI-3d·3e · 같은 이름 폴더가 이미 있음"(601:2696·601:2941),
 * "AI-4·4b · 사람별 사진 확인"(558:6268·558:6677) 값을 옮겼다.
 * 갤러리 위를 덮는 전체 화면이고, 폰 폭(480px)을 넘으면 가운데 한 줄로 모은다.
 */

/** 이름을 고른 카드의 배경·테두리. 토큰에 없는 시안 값이다. */
const PICKED_CARD_BACKGROUND = "#faf5ed";
const PICKED_CARD_BORDER = "#f3e3d3";
/** '직접 입력' 자리표시 글자색. 토큰에 없는 시안 값이다. */
const PLACEHOLDER = "#757575";

/**
 * iOS 는 16px 보다 작은 입력창에 포커스하면 화면을 확대하고 되돌리지 않는다.
 * 터치 기기에서는 16px 로 두고 줄여 그려 시안 크기(14px)로 보이게 한다 (SearchRequestField 와 같은 방법).
 */
const touchSafeFont = (width: string) => `
  @media (pointer: coarse) {
    width: calc(${width} * 16 / 14);
    /* 줄여 그려도 자리는 원래 폭만큼 차지하므로, 늘어난 만큼 오른쪽 여백을 당긴다. */
    margin-right: calc(${width} - ${width} * 16 / 14);
    font-size: 16px;
    transform: scale(0.875);
    transform-origin: left center;
  }
`;

export const Screen = styled.div`
  position: fixed;
  inset: 0;
  z-index: 950;
  display: flex;
  justify-content: center;
  background: ${colors.backgroundDefault};
`;

export const Column = styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 480px;
  height: 100%;
`;

/** 뒤로 가기(44) · 제목 · 같은 폭의 빈칸(44)으로 제목을 가운데 둔다. */
export const Header = styled.header`
  display: grid;
  flex: none;
  grid-template-columns: 44px 1fr 44px;
  align-items: center;
  height: 56px;
  padding: 0 6px;
`;

export const BackButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: ${radius.full};

  &:focus-visible {
    outline: 2px solid ${colors.primary};
  }
`;

export const HeaderTitle = styled.h1`
  font-size: 16px;
  font-weight: 700;
  text-align: center;
  color: ${colors.textStrong};
`;

export const Scroll = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
`;

export const Heading = styled.div`
  padding: 4px 20px 16px;
`;

export const Title = styled.h2`
  font-size: 22px;
  font-weight: 700;
  line-height: 30px;
  color: ${colors.textStrong};
`;

export const Subtitle = styled.p`
  margin-top: 6px;
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
  color: ${colors.textPrimary};
`;

export const Cards = styled.ul`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 16px 16px;
`;

export const Card = styled.li<{ $picked: boolean }>`
  display: flex;
  flex-direction: column;
  padding: 14px;
  border: 1px solid ${({ $picked }) => ($picked ? PICKED_CARD_BORDER : colors.borderDefault)};
  border-radius: ${radius[16]};
  background: ${({ $picked }) => ($picked ? PICKED_CARD_BACKGROUND : colors.backgroundDefault)};
`;

export const PhotoRow = styled.button`
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  text-align: left;

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 4px;
    border-radius: 10px;
  }
`;

export const Thumbs = styled.span`
  display: grid;
  flex: 1;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  min-width: 0;
`;

export const Thumb = styled.span`
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 10px;
  background: ${colors.backgroundSubtle};

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

/** 넷째 칸 위에 남은 장수를 올린다. */
export const More = styled.span`
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(26, 22, 18, 0.5);
  font-size: 13px;
  font-weight: 700;
  color: ${colors.textInverse};
`;

/** 시안의 14px 칸에 18px 아이콘을 왼쪽으로 4px 넘겨 둔다. */
export const Chevron = styled.span`
  flex: none;
  width: 14px;
  height: 18px;

  img {
    display: block;
    margin-left: -4px;
  }
`;

export const Who = styled.p`
  padding-top: 14px;
  font-size: 16px;
  font-weight: 700;
  line-height: 22px;
  color: ${colors.textStrong};
`;

export const Chips = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 10px;
`;

export const Chip = styled.button<{ $selected?: boolean }>`
  height: 34px;
  padding: 0 14px;
  border: 1px solid ${({ $selected }) => ($selected ? colors.borderPrimary : colors.borderDefault)};
  border-radius: ${radius.full};
  background: ${({ $selected }) => ($selected ? colors.primary : colors.backgroundDefault)};
  font-size: 14px;
  font-weight: 700;
  color: ${({ $selected }) => ($selected ? colors.textInverse : colors.textPrimary)};

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }
`;

/** '직접 입력' 칩. 칩 자체가 입력칸이다. */
export const ChipField = styled.form`
  display: flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  overflow: hidden;
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius.full};
  background: ${colors.backgroundDefault};

  &:focus-within {
    border-color: ${colors.borderPrimary};
  }

  input {
    width: 76px;
    height: 20px;
    padding: 0;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    font-weight: 700;
    color: ${colors.textStrong};

    &::placeholder {
      color: ${PLACEHOLDER};
    }

    ${touchSafeFont("76px")}
  }
`;

/** 같은 이름 폴더가 이미 있을 때 카드 안에 띄우는 상자 (AI-3d·3e). */
export const RenameBox = styled.div`
  display: flex;
  flex-direction: column;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[12]};
  background: ${colors.backgroundDefault};
`;

export const RenameText = styled.p`
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  color: ${colors.textPrimary};

  strong {
    font-weight: 700;
    color: ${colors.textStrong};
  }
`;

export const RenameRow = styled.form`
  display: flex;
  gap: 6px;
  align-items: center;
`;

export const RenameInput = styled.input`
  flex: 1;
  min-width: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid ${colors.borderPrimary};
  border-radius: ${radius.full};
  background: ${colors.backgroundDefault};
  outline: none;
  font-size: 14px;
  font-weight: 700;
  color: ${colors.textStrong};

  @media (pointer: coarse) {
    font-size: 16px;
  }
`;

export const SmallAction = styled.button`
  flex: none;
  align-self: flex-start;
  height: 34px;
  padding: 0 14px;
  border-radius: ${radius.full};
  background: ${colors.primary};
  font-size: 13px;
  font-weight: 700;
  color: ${colors.textInverse};

  &:disabled {
    background: ${colors.disabled};
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }
`;

/** 하단 고정 영역. 위쪽 24px 은 목록이 흐려지며 사라지는 그라데이션이다. */
export const Footer = styled.div`
  position: relative;
  display: flex;
  flex: none;
  flex-direction: column;
  padding: 12px 20px calc(16px + env(safe-area-inset-bottom, 0px));
  background: ${colors.backgroundDefault};

  &::before {
    content: "";
    position: absolute;
    top: -24px;
    left: 0;
    right: 0;
    height: 24px;
    background: linear-gradient(to bottom, rgba(255, 255, 255, 0), ${colors.backgroundDefault});
    pointer-events: none;
  }
`;

export const Hint = styled.p`
  padding-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  line-height: 19px;
  text-align: center;
  color: ${colors.textSecondary};
`;

export const Submit = styled.button`
  width: 100%;
  height: 56px;
  border-radius: ${radius[16]};
  background: ${colors.primary};
  font-size: 16px;
  font-weight: 700;
  color: ${colors.textInverse};

  &:active:not(:disabled) {
    background: ${colors.primaryPressed};
  }

  &:disabled {
    background: ${colors.backgroundSubtle};
    color: ${colors.textSecondary};
    cursor: not-allowed;
  }
`;

/* 사진 확인 화면 (AI-4·4b) */

export const GridHint = styled.p`
  padding: 0 16px 12px;
  font-size: 13px;
  font-weight: 500;
  line-height: 19px;
  color: ${colors.textPrimary};
`;

export const Grid = styled.ul`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  padding-bottom: 24px;
`;

export const GridButton = styled.button<{ $excluded: boolean }>`
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: ${colors.backgroundSubtle};

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    opacity: ${({ $excluded }) => ($excluded ? 0.45 : 1)};
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: -2px;
  }
`;

/**
 * 오른쪽 위 체크. 갤러리 사진 선택 표시(entities/media `SelectionMark`)와 같은 모양이다.
 * 넣을 사진은 주황 원에 흰 체크, 뺀 사진은 체크 없는 빈 흰 원이다.
 * 갤러리는 선택 안 된 사진에도 회색 체크를 두지만, 여기서는 해제가 "뺐음" 이라 빈 원으로 분명히 한다.
 * 사진 전체가 버튼이라 버튼인 `SelectionMark` 를 안에 둘 수 없어 같은 값으로 그린다.
 */
export const Check = styled.span<{ $checked: boolean }>`
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid ${({ $checked }) => ($checked ? colors.primary : colors.borderDisabled)};
  border-radius: ${radius.full};
  background-color: ${({ $checked }) => ($checked ? colors.primary : colors.backgroundDefault)};
  color: ${colors.textInverse};

  svg {
    width: 15px;
    height: 15px;
  }
`;

export const Centered = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px;
  font-size: 15px;
  color: ${colors.textPrimary};
`;

export const ConfirmBody = styled.div`
  display: flex;
  flex-direction: column;
  gap: 12px;

  h2 {
    font-size: 18px;
    font-weight: 700;
    line-height: 26px;
    color: ${colors.textStrong};
  }
`;
