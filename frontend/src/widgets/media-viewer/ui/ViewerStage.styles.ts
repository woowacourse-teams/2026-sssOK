import styled from "@emotion/styled";
import { ViewerButton } from "./ViewerControls.styles";

export const Stage = styled.div`
  position: relative;
  display: grid;
  flex: 1;
  min-height: 0;
  margin: 0 16px;
  overflow: hidden;
  border-radius: 14px;
  place-items: center;
  touch-action: pan-y pinch-zoom;

  @media (min-width: 768px) {
    margin-right: 48px;
    margin-left: 48px;
  }
`;

export const TapZone = styled.div<{ $side: "left" | "right" }>`
  position: absolute;
  top: 0;
  bottom: 0;
  ${({ $side }) => $side}: 0;
  width: 50%;
  -webkit-tap-highlight-color: transparent;

  @media (min-width: 768px) {
    display: none;
  }
`;

const SlideButton = styled(ViewerButton)`
  position: absolute;
  top: calc(50% - 22px);
  z-index: 1;
  width: 44px;
  height: 44px;
  border-radius: 50%;

  &:disabled {
    visibility: hidden;
  }

  svg {
    width: 26px;
    height: 26px;
  }
`;

export const PreviousButton = styled(SlideButton)`
  left: 12px;
`;

export const NextButton = styled(SlideButton)`
  right: 12px;
`;
