import styled from "@emotion/styled";
import { MediaImage } from "@/entities/media";

import { colors } from "@/shared/styles/tokens";

export const Page = styled.main`
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  overflow: hidden;
  background: #000;
  color: #fff;
`;

export const Header = styled.header`
  display: flex;
  flex: none;
  align-items: center;
  gap: 12px;
  padding: calc(14px + env(safe-area-inset-top, 0px)) 16px 14px;
`;

export const BackButton = styled.button`
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: #ffffff29;
  color: #fff;

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }
`;

export const SelectionButton = styled(BackButton)`
  border-radius: 50%;

  &:disabled {
    cursor: default;
    opacity: 0.4;
  }

  &[aria-pressed="true"] {
    background: ${colors.primary};
  }
`;

export const Counter = styled.span`
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
`;

export const Stage = styled.div`
  position: relative;
  display: grid;
  flex: 1;
  min-height: 0;
  place-items: center;
  margin: 0 16px;
  overflow: hidden;
  border-radius: 14px;

  @media (min-width: 768px) {
    margin-right: 48px;
    margin-left: 48px;
  }
`;

export const Image = styled(MediaImage)`
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  user-select: none;
`;

export const Footer = styled.footer`
  display: flex;
  flex: none;
  align-items: flex-end;
  gap: 12px;
  padding: 18px 20px calc(20px + env(safe-area-inset-bottom, 0px));
`;

export const Metadata = styled.div`
  flex: 1;
  min-width: 0;
`;

export const Uploader = styled.p`
  font-size: 15px;
  font-weight: 800;
`;

export const Subtitle = styled.p`
  margin-top: 4px;
  overflow: hidden;
  color: #a9a6a1;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const ActionButton = styled.button<{ $danger?: boolean }>`
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  color: ${({ $danger }) => ($danger ? "#ff5a5e" : "#fff")};

  &:hover:not(:disabled) {
    background: #ffffff1f;
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }

  &:disabled {
    cursor: default;
    opacity: 0.4;
  }
`;

export const StateMessage = styled.p`
  padding: 20px;
  color: #a9a6a1;
  font-size: 14px;
  text-align: center;
`;
