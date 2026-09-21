import { useEffect } from "react";

import {
  type AdminFeedbackDetail,
  describeUserAgent,
  formatFeedbackDateTime,
  useAdminFeedbackQuery,
} from "@/entities/feedback";
import { isApiError } from "@/shared/api";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import {
  Body,
  Content,
  MetaLabel,
  MetaList,
  MetaRow,
  MetaValue,
  Notice,
  RawUserAgent,
  Title,
} from "./FeedbackDetailModal.styles";

interface FeedbackDetailModalProps {
  token: string;
  feedbackId: number;
  onClose: () => void;
  /** 401. 이 토큰으로는 무엇을 불러도 같은 답이 온다 — 로그인부터 다시 밟게 한다. */
  onUnauthorized: () => void;
}

/**
 * 의견 한 건의 전문과 맥락 (#245).
 *
 * 목록에서 받은 값을 넘겨받지 않고 단건 API 로 다시 부른다. 목록의 `content` 는 서버가
 * 100자에서 자른 것이라, 그걸 먼저 보여주면 전문처럼 읽힌다.
 */
export const FeedbackDetailModal = ({
  token,
  feedbackId,
  onClose,
  onUnauthorized,
}: FeedbackDetailModalProps) => {
  const { data, error, isPending, refetch } = useAdminFeedbackQuery(token, feedbackId);

  const status = isApiError(error) ? error.status : null;

  useEffect(() => {
    if (status === 401) onUnauthorized();
  }, [status, onUnauthorized]);

  return (
    <Modal size="lg" title={<Title>의견 #{feedbackId}</Title>} onClose={onClose}>
      <Content>
        {isPending && <Notice>의견을 불러오고 있어요.</Notice>}

        {/* 로그인 화면으로 옮기는 중이라 여기서 또 안내하지 않는다. */}
        {error !== null && status !== 401 && (
          <Notice>
            {status === 403
              ? "이 계정으로는 의견을 볼 수 없어요."
              : status === 404
                ? "없어진 의견이에요."
                : "의견을 불러오지 못했어요."}
            {/* 권한 없음·없는 의견은 다시 눌러도 같은 답이 온다. */}
            {status !== 403 && status !== 404 && (
              <Button size="sm" variant="default" onClick={() => void refetch()}>
                다시 시도
              </Button>
            )}
          </Notice>
        )}

        {data && <FeedbackDetail feedback={data} />}
      </Content>
    </Modal>
  );
};

const FeedbackDetail = ({ feedback }: { feedback: AdminFeedbackDetail }) => {
  const { device, browser } = describeUserAgent(feedback.userAgent);
  const described = [device, browser].filter((part): part is string => part !== null).join(" · ");

  return (
    <>
      <Body>{feedback.content}</Body>

      <MetaList>
        <MetaRow>
          <MetaLabel>방</MetaLabel>
          <MetaValue>{`${feedback.roomId} · ${feedback.roomName}`}</MetaValue>
        </MetaRow>
        <MetaRow>
          <MetaLabel>작성자</MetaLabel>
          {/* 닉네임은 회원이 정리된 뒤면 null 이다. 멤버 번호만으로도 누군지는 가려진다. */}
          <MetaValue>
            {feedback.nickname === null
              ? String(feedback.memberId)
              : `${feedback.memberId} · ${feedback.nickname}`}
          </MetaValue>
        </MetaRow>
        <MetaRow>
          <MetaLabel>기기</MetaLabel>
          <MetaValue>
            {feedback.userAgent === null ? (
              "알 수 없음"
            ) : (
              <>
                {/* 규칙에 걸리지 않는 UA(curl 등)는 해석한 줄 없이 원본만 남긴다. */}
                {described !== "" && <span>{described}</span>}
                <RawUserAgent>{feedback.userAgent}</RawUserAgent>
              </>
            )}
          </MetaValue>
        </MetaRow>
        <MetaRow>
          <MetaLabel>버전</MetaLabel>
          <MetaValue>
            {feedback.frontendVersion === null ? (
              "알 수 없음"
            ) : (
              <Badge size="sm" variant="soft">
                {feedback.frontendVersion}
              </Badge>
            )}
          </MetaValue>
        </MetaRow>
        <MetaRow>
          <MetaLabel>작성 시각</MetaLabel>
          <MetaValue>{formatFeedbackDateTime(feedback.createdAt) ?? "알 수 없음"}</MetaValue>
        </MetaRow>
      </MetaList>
    </>
  );
};
