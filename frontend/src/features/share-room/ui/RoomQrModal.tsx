import styled from "@emotion/styled";
import { QRCodeSVG } from "qrcode.react";

import { ROUTES } from "@/shared/config";
import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { Modal } from "@/shared/ui/modal";

interface RoomQrModalProps {
  roomCode: string;
  onClose: () => void;
}

/** 소리 내어 읽거나 옮겨 적기 쉽게 4자리씩 끊는다. */
const formatRoomCode = (code: string): string => code.match(/.{1,4}/g)?.join(" ") ?? code;

export const RoomQrModal = ({ roomCode, onClose }: RoomQrModalProps) => {
  const url = new URL(ROUTES.roomInvite(roomCode), window.location.origin).href;

  return (
    <Modal title={<Title>QR 코드로 공유</Title>} onClose={onClose}>
      <Content>
        <QrCode>
          <QRCodeSVG value={url} size={160} level="M" marginSize={1} title="현재 방 참여 QR 코드" />
        </QrCode>
        <CodeBox>
          <CodeLabel>방 코드</CodeLabel>
          <Code aria-label={`방 코드 ${roomCode.split("").join(" ")}`}>
            {formatRoomCode(roomCode)}
          </Code>
        </CodeBox>
        <Description>
          QR 코드를 찍거나 앱에서 코드를 입력하면
          <br />이 방에 참여할 수 있어요.
        </Description>
      </Content>
    </Modal>
  );
};

const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;

const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[20]};
  padding-top: ${spacing[8]};
`;

const QrCode = styled.div`
  display: flex;
  padding: ${spacing[8]};
  background: ${colors.backgroundDefault};
  border: 2px solid ${colors.borderPrimary};
  border-radius: ${radius[16]};
`;

const CodeBox = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[4]};
`;

const CodeLabel = styled.span`
  color: ${colors.textSecondary};

  ${typography.caption1}
`;

const Code = styled.p`
  color: ${colors.textStrong};
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.08em;

  ${typography.heading2}
`;

const Description = styled.p`
  color: ${colors.textSecondary};
  text-align: center;

  ${typography.body}
`;
