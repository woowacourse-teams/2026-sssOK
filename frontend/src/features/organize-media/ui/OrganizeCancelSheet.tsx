import { BottomSheet } from "@/shared/ui/bottom-sheet";

import type { OrganizeKind } from "../api/types";
import { Body, Later, Stop, Submit } from "./OrganizeSheet.styles";

/** `running` 은 나누는 중에 멈출 때, `result` 는 다 나눈 결과 알림을 닫을 때다. */
type OrganizeCancelKind = "running" | "result";

type Copy = Record<OrganizeCancelKind, { title: string; body: string; keep: string; stop: string }>;

const COPY: Record<OrganizeKind, Copy> = {
  PERSON: {
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
  },
  // 피그마 G-S3b · 찾기 멈추기 확인(660:2731)
  SIMILAR: {
    running: {
      title: "사진 묶기를 멈출까요?",
      body: "지금까지 묶은 결과는 사라져요.",
      keep: "계속 묶기",
      stop: "멈추기",
    },
    result: {
      title: "정리한 결과를 취소할까요?",
      body: "취소하면 묶은 결과는 사라져요.",
      keep: "결과 남겨 두기",
      stop: "취소하기",
    },
  },
};

interface OrganizeCancelSheetProps {
  kind?: OrganizeCancelKind;
  /** 멈출 작업의 종류. 문구와 버리는 쪽 버튼 모양이 갈린다. */
  organizeKind?: OrganizeKind;
  onKeepGoing: () => void;
  onStop: () => void;
}

/**
 * 나누기를 멈추거나 결과를 버리기 전에 한 번 더 묻는다 (피그마 G-3b · 560:177).
 * 결과를 지키는 쪽을 주황 버튼으로 앞에 두고, 바깥을 누르면 지키는 쪽이다.
 * 버리는 쪽은 사람별이면 회색 글자 버튼, 비슷한 사진이면 테두리 버튼(G-S3b)이다.
 */
export const OrganizeCancelSheet = ({
  kind = "running",
  organizeKind = "PERSON",
  onKeepGoing,
  onStop,
}: OrganizeCancelSheetProps) => {
  const copy = COPY[organizeKind][kind];
  const StopButton = organizeKind === "SIMILAR" ? Stop : Later;

  return (
    <BottomSheet title={copy.title} variant="action" onClose={onKeepGoing}>
      {(close) => (
        <>
          <Body>{copy.body}</Body>
          <Submit type="button" onClick={close}>
            {copy.keep}
          </Submit>
          <StopButton type="button" onClick={onStop}>
            {copy.stop}
          </StopButton>
        </>
      )}
    </BottomSheet>
  );
};
