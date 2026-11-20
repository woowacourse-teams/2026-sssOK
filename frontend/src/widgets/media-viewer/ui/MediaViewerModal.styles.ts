import styled from "@emotion/styled";

export const Dialog = styled.dialog`
  position: fixed;
  inset: 0;
  width: 100%;
  max-width: none;
  height: 100dvh;
  max-height: none;
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  background: #000;
  color: #fff;

  &[open] {
    display: flex;
    flex-direction: column;
  }

  &:focus {
    outline: none;
  }

  &::backdrop {
    background: #000;
  }
`;

export const StateMessage = styled.p`
  padding: 20px;
  color: #a9a6a1;
  font-size: 14px;
  text-align: center;
`;
