import styled from "@emotion/styled";
import { colors } from "@/shared/styles/tokens";

export const ViewerButton = styled.button`
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

export const LikeActionButton = styled(ActionButton)<{ $liked: boolean }>`
  display: flex;
  gap: 4px;
  width: auto;
  min-width: 40px;
  padding: 0 8px;
  color: ${({ $liked }) => ($liked ? colors.danger : "#fff")};
  font-size: 12px;
  font-variant-numeric: tabular-nums;
`;
