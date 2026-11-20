import styled from "@emotion/styled";
import { ActionButton } from "./ViewerControls.styles";

export const MoreActions = styled.div`
  position: relative;
  flex: none;
`;
export const MoreButton = styled(ActionButton)`
  display: none;
  @media (max-width: 767px) {
    display: grid;
  }
`;
export const ActionList = styled.div<{ $open: boolean }>`
  display: flex;
  gap: 12px;
  @media (max-width: 767px) {
    position: absolute;
    right: 0;
    bottom: calc(100% + 8px);
    z-index: 1;
    display: ${({ $open }) => ($open ? "flex" : "none")};
    flex-direction: column;
    gap: 0;
    width: 204px;
    padding: 6px;
    border: 1px solid #ffffff29;
    border-radius: 14px;
    background: #242424;
    box-shadow: 0 8px 24px #0006;
  }
`;
export const MoreActionButton = styled(ActionButton)`
  @media (max-width: 767px) {
    display: flex;
    gap: 12px;
    width: 100%;
    min-height: 44px;
    height: auto;
    padding: 10px 12px;
    text-align: left;
    font-size: 14px;
    svg {
      flex: none;
    }
  }
`;
export const ActionLabel = styled.span`
  @media (min-width: 768px) {
    display: none;
  }
`;
