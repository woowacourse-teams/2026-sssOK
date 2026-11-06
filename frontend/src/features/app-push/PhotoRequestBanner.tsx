import styled from "@emotion/styled";
import { createPortal } from "react-dom";
import { HiOutlineBell, HiXMark } from "react-icons/hi2";

import { colors, radius, shadow, typography } from "@/shared/styles/tokens";

interface Props {
  title?: string;
  message: string;
  roomPath: string;
  onClose: () => void;
}

export const PhotoRequestBanner = ({
  title = "사진 요청이 왔어요",
  message,
  roomPath,
  onClose,
}: Props) =>
  createPortal(
    <Position>
      <Card aria-label="받은 사진 요청">
        <Bell aria-hidden="true">
          <HiOutlineBell />
        </Bell>
        <Content>
          <div role="status" aria-live="polite" aria-atomic="true">
            <Title>{title}</Title>
            <Message>{message}</Message>
          </div>
          {window.location.pathname !== roomPath && (
            <RoomLink href={roomPath}>방으로 가기 →</RoomLink>
          )}
        </Content>
        <Close type="button" aria-label="사진 요청 알림 닫기" onClick={onClose}>
          <HiXMark aria-hidden="true" />
        </Close>
      </Card>
    </Position>,
    document.querySelector("dialog:modal") ?? document.body,
  );

const Position = styled.div`
  position: fixed;
  top: calc(28px + env(safe-area-inset-top, 0px));
  left: 50%;
  transform: translateX(-50%);
  z-index: 1100;
  width: min(100%, 480px);
  padding: 0 16px;
  box-sizing: border-box;
  pointer-events: none;
`;

const Card = styled.aside`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: ${colors.backgroundDefault};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[24]};
  box-shadow: ${shadow.modal};
  pointer-events: auto;
`;

const Bell = styled.span`
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 40px;
  color: ${colors.primary};
  background: ${colors.primarySubtle};
  border-radius: ${radius[12]};
  svg {
    width: 22px;
    height: 22px;
  }
`;

const Content = styled.div`
  flex: 1;
  min-width: 0;
`;

const Title = styled.p`
  margin-bottom: 4px;
  color: ${colors.textStrong};
  ${typography.caption1}
  font-weight: 700;
`;

const Message = styled.p`
  color: ${colors.textPrimary};
  overflow-wrap: anywhere;
  ${typography.caption1}
`;

const RoomLink = styled.a`
  display: inline-block;
  margin-top: 10px;
  color: ${colors.primaryPressed};
  ${typography.caption1}
  font-weight: 700;
  text-underline-offset: 3px;
`;

const Close = styled.button`
  display: grid;
  place-items: center;
  flex: none;
  width: 32px;
  height: 32px;
  margin: -4px -4px 0 0;
  color: ${colors.textSecondary};
  border-radius: ${radius.full};
  svg {
    width: 20px;
    height: 20px;
  }
  &:hover {
    background: ${colors.backgroundSubtle};
  }
  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }
`;
