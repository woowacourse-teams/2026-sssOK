import { useId } from "react";
import { createPortal } from "react-dom";

import { Body, Dialog, Handle, Later, Overlay, Submit, Title } from "./OrganizeSheet.styles";

/** `running` 은 나누는 중에 멈출 때, `result` 는 다 나눈 결과 알림을 닫을 때다. */
type OrganizeCancelKind = "running" | "result";

const COPY: Record<
  OrganizeCancelKind,
  { title: string; body: string; keep: string; stop: string }
> = {
  running: {
    title: "나누기를 멈출까요?",
    body: "지금까지 나눈 결과는 사라져요.",
    keep: "계속 나누기",
    stop: "멈추기",
  },
  result: {
    title: "정리한 결과를 취소할까요?",
    body: "취소하면 나눈 결과는 사라져요.",
    keep: "결과 남겨 두기",
    stop: "취소하기",
  },
};

interface OrganizeCancelSheetProps {
  kind?: OrganizeCancelKind;
  onKeepGoing: () => void;
  onStop: () => void;
}

/**
 * 나누기를 멈추거나 결과를 버리기 전에 한 번 더 묻는다 (피그마 G-3b · 560:177).
 * 결과를 지키는 쪽을 주황 버튼으로 앞에 두고, 버리는 쪽은 회색 글자 버튼이다. 바깥을 누르면 지키는 쪽이다.
 */
export const OrganizeCancelSheet = ({
  kind = "running",
  onKeepGoing,
  onStop,
}: OrganizeCancelSheetProps) => {
  const titleId = useId();
  const copy = COPY[kind];

  return createPortal(
    <Overlay data-testid="organize-cancel-overlay" onClick={onKeepGoing}>
      <Dialog
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
      >
        <Handle aria-hidden="true" />
        <Title id={titleId}>{copy.title}</Title>
        <Body>{copy.body}</Body>
        <Submit type="button" onClick={onKeepGoing}>
          {copy.keep}
        </Submit>
        <Later type="button" onClick={onStop}>
          {copy.stop}
        </Later>
      </Dialog>
    </Overlay>,
    document.body,
  );
};
