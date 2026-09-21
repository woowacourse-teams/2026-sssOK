import { Badge } from "@/shared/ui/badge";
import { describeUserAgent } from "../lib/describeUserAgent";
import { formatFeedbackTime } from "../lib/formatFeedbackTime";
import type { AdminFeedback } from "../model/types";
import { Content, Item, Meta, MetaText } from "./FeedbackItem.styles";

interface FeedbackItemProps {
  feedback: AdminFeedback;
}

export const FeedbackItem = ({ feedback }: FeedbackItemProps) => {
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

  return (
    <Item>
      <Content>{feedback.content}</Content>
      <Meta>
        <MetaText>{meta.join(" · ")}</MetaText>
        {feedback.frontendVersion !== null && (
          <Badge size="sm" variant="soft">
            {feedback.frontendVersion}
          </Badge>
        )}
      </Meta>
    </Item>
  );
};
