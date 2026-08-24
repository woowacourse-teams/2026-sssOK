import styled from "@emotion/styled";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { colors, radius, shadow, spacing, typography } from "@/shared/styles/tokens";

export interface BottomSheetProps {
  title: string;
  onClose?: () => void;
  children?: ReactNode;
}

export const BottomSheet = ({ title, onClose, children }: BottomSheetProps) => {
  return createPortal(
    <Overlay data-testid="bottom-sheet-overlay" onClick={onClose}>
      <Sheet onClick={(event) => event.stopPropagation()}>
        <Handle />
        <Title>{title}</Title>
        {children && <Body>{children}</Body>}
      </Sheet>
    </Overlay>,
    document.body,
  );
};

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: ${colors.overlay};
  z-index: 1000;
`;

const Sheet = styled.div`
  width: 100%;
  max-width: 390px;
  padding: ${spacing[16]} ${spacing[24]} ${spacing[24]};
  background: ${colors.backgroundDefault};
  border-radius: ${radius[24]};
  box-shadow: ${shadow.sheet};
`;

const Handle = styled.div`
  width: 50px;
  height: 5px;
  margin: 0 auto ${spacing[16]};
  border-radius: ${radius.full};
  background: ${colors.borderDefault};
`;

const Title = styled.h2`
  font-size: ${typography.label1.fontSize};
  font-weight: ${typography.label1.fontWeight};
  line-height: ${typography.label1.lineHeight};
  color: ${colors.textStrong};
`;

const Body = styled.div`
  width: 100%;
  margin-top: ${spacing[16]};
`;
