import styled from "@emotion/styled";

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

export const ErrorMessage = styled.p`
  margin-top: 4px;
  color: #ff7a7e;
  font-size: 12px;
`;
