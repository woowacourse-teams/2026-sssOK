import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

/** 감싼 자리(OnboardingSection 의 PreviewArea)의 높이를 그대로 채운다 */
export const Preview = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 280px;
  border-radius: ${radius[24]};
  background: ${colors.backgroundSubtle};
  overflow: hidden;
`;

export const SceneStage = styled.div`
  position: relative;
  flex: 1;
  overflow: hidden;
`;

/** 장면을 그린 크기 그대로 두고, 칸 크기에 맞춰 통째로 확대한다 */
export const SceneCanvas = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform-origin: center;
`;

/** 세 장면을 겹쳐 두고 지금 장면만 드러낸다. 자리를 바꾸지 않아 높이가 흔들리지 않는다. */
export const Scene = styled.div<{ $active: boolean }>`
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${spacing[12]};
  opacity: ${({ $active }) => ($active ? 1 : 0)};
  transition: opacity 350ms ease;

  /* 장면에 들어올 때마다 안의 등장 효과를 처음부터 다시 튼다 */
  ${({ $active }) => !$active && "* { animation: none !important; }"}

  @keyframes onboarding-pop {
    from {
      opacity: 0;
      transform: translateY(6px) scale(0.96);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;

    * {
      animation: none !important;
    }
  }
`;

const sceneCard = `
  width: 100%;
`;

/**
 * 단톡방 그림에만 쓰는 색. 앱 토큰이 아니라 "단체 채팅방" 을 떠올리게 하는 삽화 색이다.
 * 로고나 서비스 이름은 넣지 않는다.
 */
const CHAT = {
  room: "#B6CCE0",
  roomName: "#36485A",
  myBubble: "#FEE500",
} as const;

/** 240px 폭으로 그린 대화를 넓어진 장면(343px)에 맞춰 키운다 */
export const ChatCard = styled.div`
  width: 240px;
  transform: scale(1.35);
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: ${spacing[12]} 10px;
  border-radius: ${radius[16]};
  background: ${CHAT.room};
`;

export const ChatRoomName = styled.span`
  margin-bottom: 2px;
  color: ${CHAT.roomName};
  text-align: center;
  ${typography.caption4};
`;

export const LinkBubble = styled.div`
  align-self: flex-end;
  max-width: 88%;
  padding: ${spacing[8]} 10px;
  border-radius: ${radius[12]} ${radius[12]} 2px ${radius[12]};
  background: ${CHAT.myBubble};
  color: ${colors.textStrong};
  ${typography.caption1};
  font-size: 12px;
  line-height: 16px;
  animation: onboarding-pop 400ms 700ms both;
`;

export const LinkPreview = styled.span`
  display: block;
  margin-top: 6px;
  padding: 6px ${spacing[8]};
  border-radius: 8px;
  background: ${colors.backgroundDefault};
  color: ${colors.textPrimary};
  ${typography.caption3};
  font-size: 11px;

  b {
    display: block;
    color: ${colors.textStrong};
    ${typography.caption2};
  }
`;

export const ReplyBubble = styled.div<{ $delay: number }>`
  align-self: flex-start;
  padding: 6px 10px;
  border-radius: ${radius[12]} ${radius[12]} ${radius[12]} 2px;
  background: ${colors.backgroundDefault};
  color: ${colors.textStrong};
  ${typography.caption2};
  animation: onboarding-pop 400ms ${({ $delay }) => $delay}ms both;
`;

/**
 * 높이를 고정하고 폭은 장면을 따라간다. 휴대폰에서는 칸이 정사각형이고,
 * 장면이 옆으로 넓어지는 PC 에서는 칸이 가로로 늘어 빈 곳 없이 채운다.
 */
export const Album = styled.div`
  ${sceneCard}
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 3px;
  flex: none;
  height: 233px;
  padding: ${spacing[8]};
  border-radius: ${radius[16]};
  background: ${colors.backgroundDefault};
`;

export const Photo = styled.div<{ $color: string }>`
  position: relative;
  min-width: 0;
  min-height: 0;
  border-radius: 6px;
  background: ${({ $color }) => $color};
  overflow: hidden;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
`;

/**
 * 실제 갤러리 카드의 업로더 닉네임 뱃지(`UploaderBadge`)를 줄여 그린 것.
 * 내 사진은 주황, 다른 사람 사진은 흰 바탕으로 구분한다.
 */
export const PhotoOwner = styled.span<{ $mine: boolean }>`
  position: absolute;
  z-index: 1;
  left: 6px;
  bottom: 6px;
  max-width: calc(100% - 12px);
  height: 18px;
  padding: 0 7px;
  overflow: hidden;
  border-radius: ${radius.full};
  background: ${({ $mine }) => ($mine ? colors.primary : "rgba(255, 255, 255, 0.94)")};
  color: ${({ $mine }) => ($mine ? colors.textInverse : colors.textPrimary)};
  font-size: 9px;
  font-weight: 700;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const PhotoCheck = styled.span<{ $picked: boolean }>`
  position: absolute;
  z-index: 1;
  top: 6px;
  right: 6px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 2px solid ${({ $picked }) => ($picked ? colors.primary : colors.textInverse)};
  border-radius: ${radius.full};
  background: ${({ $picked }) => ($picked ? colors.primary : "rgba(0, 0, 0, 0.15)")};
  color: ${colors.textInverse};
  ${({ $picked }) => $picked && "animation: onboarding-pop 250ms both;"}

  svg {
    width: 13px;
    height: 13px;
  }
`;

export const StepList = styled.ol`
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: ${spacing[4]};
  padding: 10px;
`;

export const StepItem = styled.li`
  display: flex;
`;

/** 누르면 그 장면으로 건너뛴다. 자동으로 넘어가기를 기다리지 않아도 되게 한다. */
export const StepButton = styled.button`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  padding: 8px ${spacing[4]} 6px;
  border-radius: 10px;
  text-align: center;

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }
`;

export const StepTitle = styled.span<{ $active: boolean }>`
  color: ${({ $active }) => ($active ? colors.textStrong : colors.textSecondary)};
  ${typography.caption1};
  font-size: 15px;
  font-weight: 800;
  white-space: nowrap;

  /* 휴대폰은 한 칸(약 97px)에 넘치지 않는 만큼만 키우고, 넓은 화면에서 더 키운다 */
  @media (min-width: 768px) {
    font-size: 16px;
    line-height: 22px;
  }
`;

export const HiddenText = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  border: 0;
  clip: rect(0 0 0 0);
  white-space: nowrap;
`;

/** 지금 단계가 얼마나 남았는지 차오르는 띠. 다 찬 단계는 채운 채로 둔다. */
export const StepProgress = styled.span<{
  $state: "idle" | "running" | "done";
  $duration: number;
}>`
  position: relative;
  width: 100%;
  height: 3px;
  margin-top: 6px;
  border-radius: ${radius.full};
  background: ${colors.borderDefault};
  overflow: hidden;

  &::after {
    content: "";
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: ${colors.primary};
    transform-origin: left;
    transform: scaleX(${({ $state }) => ($state === "done" ? 1 : 0)});
    ${({ $state, $duration }) =>
      $state === "running" && `animation: onboarding-fill ${$duration}ms linear forwards;`}
  }

  @keyframes onboarding-fill {
    to {
      transform: scaleX(1);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    &::after {
      animation: none;
    }
  }
`;
