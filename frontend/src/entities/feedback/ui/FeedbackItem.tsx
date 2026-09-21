import { Badge } from "@/shared/ui/badge";
import { describeUserAgent } from "../lib/describeUserAgent";
import { formatFeedbackTime } from "../lib/formatFeedbackTime";
import type { AdminFeedback } from "../model/types";
import { Content, Item, Meta, MetaText, SelectButton } from "./FeedbackItem.styles";

interface FeedbackItemProps {
  feedback: AdminFeedback;
  /** 넘기면 항목 전체가 버튼이 된다. 누르면 이 의견을 연다. */
  onSelect?: (feedbackId: number) => void;
}

export const FeedbackItem = ({ feedback, onSelect }: FeedbackItemProps) => {
  const { device, browser } = describeUserAgent(feedback.userAgent);

  /*
   * 읽어내지 못한 값은 자리를 비운다.
   *
   * UA 없이 들어온 의견(curl·봇·구버전 클라이언트)이 정상 값이라, 기기·브라우저가 없다고
   * 항목이 빠지거나 `방 128 · 멤버 871 ·  ·  · 09-16 08:41` 처럼 가운뎃점만 남아서는 안 된다.
   */
  const meta = [
    `방 ${feedback.roomId}`,
    `멤버 ${feedback.memberId}`,
    device,
    browser,
    formatFeedbackTime(feedback.createdAt),
  ].filter((part): part is string => part !== null);

  const body = (
    <>
      <Content>{feedback.content}</Content>
      <Meta>
        <MetaText>{meta.join(" · ")}</MetaText>
        {feedback.frontendVersion !== null && (
          <Badge size="sm" variant="soft">
            {feedback.frontendVersion}
          </Badge>
        )}
      </Meta>
    </>
  );

  // li 는 목록의 한 칸으로 남기고 버튼은 그 안에 둔다 — li 에 onClick 을 달면 키보드로 열 수 없다.
  return (
    <Item selectable={onSelect !== undefined}>
      {onSelect ? (
        <SelectButton type="button" onClick={() => onSelect(feedback.feedbackId)}>
          {body}
        </SelectButton>
      ) : (
        body
      )}
    </Item>
  );
};
