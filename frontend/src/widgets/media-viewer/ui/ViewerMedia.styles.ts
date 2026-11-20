import styled from "@emotion/styled";

export const ViewerPhoto = styled.img<{ $visible: boolean }>`
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  visibility: ${({ $visible }) => ($visible ? "visible" : "hidden")};
  user-select: none;
`;

export const ViewerVideoPlayer = styled.video`
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
`;
