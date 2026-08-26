import { HiXMark } from "react-icons/hi2";

import type { MediaSelection, RejectedSelection } from "../model/selectMediaFiles";
import {
  DismissButton,
  Notice,
  NoticeBody,
  ReasonList,
  RejectedCount,
  SelectedCount,
} from "./SelectionNotice.styles";

interface SelectionNoticeProps {
  selection: MediaSelection;
  onDismiss: () => void;
}

/**
 * 방금 고른 결과를 알린다. 스크린리더에 읽히는 건 이걸 감싼 `MediaUploader` 의 라이브 영역이다. 걸러진 파일이 있으면 **몇 장이 왜 빠졌는지** 같이 보여준다 —
 * 장수만 줄어든 채 넘어가면 사용자는 사진이 사라진 줄 안다.
 */
export const SelectionNotice = ({ selection, onDismiss }: SelectionNoticeProps) => {
  const { accepted, rejected } = selection;

  return (
    <Notice>
      <NoticeBody>
        {accepted.length > 0 && <SelectedCount>{accepted.length}장을 선택했어요</SelectedCount>}
        {rejected.length > 0 && (
          <>
            <RejectedCount>{rejected.length}장은 올릴 수 없어요</RejectedCount>
            <ReasonList>
              {summarizeReasons(rejected).map(({ message, count }) => (
                <li key={message}>
                  {message} ({count}장)
                </li>
              ))}
            </ReasonList>
          </>
        )}
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
