import { HiXMark } from "react-icons/hi2";

import type { MediaSelection, RejectedSelection } from "../model/selectMediaFiles";
import {
  DismissButton,
  Notice,
  NoticeBody,
  ReasonList,
  RejectedCount,
} from "./SelectionNotice.styles";

interface SelectionNoticeProps {
  selection: MediaSelection;
  onDismiss: () => void;
}

/**
 * 고른 파일 중 **못 올리는 것**을 알린다. 몇 장이 왜 빠졌는지까지 보여준다 —
 * 장수만 줄어든 채 넘어가면 사용자는 사진이 사라진 줄 안다.
 * 스크린리더에 읽히는 건 이걸 감싼 `MediaUploader` 의 라이브 영역이다.
 *
 * 올라갈 장수는 여기서 말하지 않는다. 요구사항이 아니고(#72 완료 조건은 걸러진 장수만이다),
 * 진행 바(#73)가 `0 / 8` 로 같은 말을 하게 된다.
 */
export const SelectionNotice = ({ selection, onDismiss }: SelectionNoticeProps) => {
  const { rejected } = selection;

  // 걸러진 것이 없으면 할 말이 없다. 빈 카드에 닫기 버튼만 남는 걸 막는다.
  if (rejected.length === 0) {
    return null;
  }

  return (
    <Notice>
      <NoticeBody>
        <RejectedCount>{rejected.length}장은 올릴 수 없어요</RejectedCount>
        <ReasonList>
          {summarizeReasons(rejected).map(({ message, count }) => (
            <li key={message}>
              {message} ({count}장)
            </li>
          ))}
        </ReasonList>
      </NoticeBody>
      <DismissButton type="button" onClick={onDismiss} aria-label="알림 닫기">
        <HiXMark aria-hidden />
      </DismissButton>
    </Notice>
  );
};

/** 파일명을 한 줄씩 늘어놓으면 30장을 걸렀을 때 화면을 덮는다. 사유별 장수로 접는다. */
const summarizeReasons = (rejected: RejectedSelection[]) => {
  const countByMessage = new Map<string, number>();

  rejected.forEach(({ message }) => {
    countByMessage.set(message, (countByMessage.get(message) ?? 0) + 1);
  });

  return [...countByMessage].map(([message, count]) => ({ message, count }));
};
