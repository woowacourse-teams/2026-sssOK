import styled from "@emotion/styled";
import { QRCodeSVG } from "qrcode.react";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { Modal } from "@/shared/ui/modal";

interface RoomQrModalProps {
  onClose: () => void;
}

export const RoomQrModal = ({ onClose }: RoomQrModalProps) => {
  const url = window.location.href;

  return (
    <Modal title={<Title>QR 코드로 공유</Title>} onClose={onClose}>
      <Content>
        <QrCode>
          <QRCodeSVG value={url} size={160} level="M" marginSize={1} title="현재 방 참여 QR 코드" />
        </QrCode>
        <Description>
          다른 기기에서 QR 코드를 스캔하면
          <br />이 방에 바로 참여할 수 있어요.
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

const Description = styled.p`
  color: ${colors.textSecondary};
  text-align: center;

  ${typography.body}
`;
