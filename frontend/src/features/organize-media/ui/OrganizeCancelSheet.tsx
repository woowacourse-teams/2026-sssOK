import { useId } from "react";
import { createPortal } from "react-dom";

import { Body, Dialog, Handle, Later, Overlay, Submit, Title } from "./OrganizeSheet.styles";

interface OrganizeCancelSheetProps {
  onKeepGoing: () => void;
  onStop: () => void;
}

/**
 * 진행 바의 '취소' 를 누르면 한 번 더 묻는다 (피그마 G-3b · 560:177).
 * 계속하는 쪽을 주황 버튼으로 앞에 두고, 멈추기는 회색 글자 버튼이다. 바깥을 누르면 계속 나눈다.
 */
export const OrganizeCancelSheet = ({ onKeepGoing, onStop }: OrganizeCancelSheetProps) => {
  const titleId = useId();

  return createPortal(
    <Overlay data-testid="organize-cancel-overlay" onClick={onKeepGoing}>
      <Dialog
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
      >
        <Handle aria-hidden="true" />
        <Title id={titleId}>나누기를 멈출까요?</Title>
        <Body>지금까지 나눈 결과는 사라지고, 방 사진은 그대로예요.</Body>
        <Submit type="button" onClick={onKeepGoing}>
          계속 나누기
        </Submit>
        <Later type="button" onClick={onStop}>
          멈추기
        </Later>
      </Dialog>
    </Overlay>,
    document.body,
  );
};
