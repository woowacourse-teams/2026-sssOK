import { FloatingBar } from "@/shared/ui/floating-bar";

import type { OrganizeKind } from "../api/types";
import closeIcon from "../assets/close.svg";
import spinnerIcon from "../assets/spinner.svg";
import alertCircle from "../assets/status-alert-circle.svg";
import alertDot from "../assets/status-alert-dot.svg";
import alertLine from "../assets/status-alert-line.svg";
import successCheck from "../assets/status-success-check.svg";
import successCircle from "../assets/status-success-circle.svg";
import type { MyOrganizeJob } from "../model/useOrganizeJob";
import {
  ActionButton,
  CancelButton,
  CloseButton,
  Count,
  Dock,
  Fill,
  Message,
  Spinner,
  StateIcon,
  Status,
  StatusText,
} from "./OrganizeStatusBar.styles";

interface OrganizeStatusBarProps {
  job: MyOrganizeJob;
  onCancel: () => void;
  onReview: () => void;
  onRetry: () => void;
  onDismiss: () => void;
}

/**
 * 피그마의 "29 / 62" 자리. 서버는 단계마다 0부터 다시 세므로 그 숫자 대신,
 * 전체 진행률만큼 사진을 본 것으로 보고 장수로 바꿔 보여 준다. 그래서 숫자도 앞으로만 간다.
 * 진행률이 아직 없으면 비워 둔다. "0 / 41" 로 두면 0장째에 멈춘 것처럼 보인다.
 */
const countOf = ({ photoCount, percent }: MyOrganizeJob) =>
  photoCount === null || percent === null
    ? null
    : `${Math.floor((percent / 100) * photoCount)} / ${photoCount}`;

/**
 * 종류마다 다른 문구. 사람별은 "나누기", 비슷한 사진은 "묶기" 다
 * (피그마 G-S3·G-S4·G-S4b·G-S4c · 649:2696).
 */
const COPY: Record<
  OrganizeKind,
  {
    progressLabel: string;
    running: (percent: number) => string;
    ready: (groupCount: number | null) => string;
    failed: string;
    empty: string;
  }
> = {
  PERSON: {
    progressLabel: "나누기 진행률",
    running: (percent) => `나누는 중... ${percent}%`,
    ready: (count) => (count === null ? "사진을 다 나눴어요" : `${count}명을 찾았어요`),
    failed: "사진을 나누지 못했어요",
    empty: "사람이 찍힌 사진을 찾지 못했어요",
  },
  SIMILAR: {
    progressLabel: "묶기 진행률",
    running: (percent) => `비슷한 사진들 묶는 중... ${percent}%`,
    ready: (count) =>
      count === null ? "사진을 다 묶었어요" : `비슷한 사진을 ${count}개로 묶었어요`,
    failed: "사진을 묶지 못했어요",
    empty: "비슷한 사진을 찾지 못했어요",
  },
};

/**
 * RUNNING 이어도 분석 서버는 사진을 받아 오고 모델을 불러오는 동안 단계를 주지 않는다.
 * 그때 진행률이 null 이라 "나누는 중" 대신 준비 중이라고 알린다.
 */
const runningLabelOf = (kind: OrganizeKind, percent: number | null) =>
  percent === null ? "사진 준비 중..." : COPY[kind].running(percent);

/* 아이콘 조각 위치는 피그마 레이어 그대로다(22px 칸 기준). */
const SuccessIcon = () => (
  <StateIcon aria-hidden="true">
    <img src={successCircle} alt="" width={20} height={20} style={{ left: 2, top: 2 }} />
    <img src={successCheck} alt="" width={10} height={7.6} style={{ left: 7, top: 8.5 }} />
  </StateIcon>
);

const AlertIcon = () => (
  <StateIcon aria-hidden="true">
    <img src={alertCircle} alt="" width={20} height={20} style={{ left: 2, top: 2 }} />
    <img src={alertLine} alt="" width={2} height={7.5} style={{ left: 11, top: 6.5 }} />
    <img src={alertDot} alt="" width={2.4} height={2.4} style={{ left: 10.8, top: 15.3 }} />
  </StateIcon>
);

const Close = ({ onClick }: { onClick: () => void }) => (
  <CloseButton type="button" aria-label="알림 닫기" onClick={onClick}>
    <img src={closeIcon} alt="" width={18} height={18} />
  </CloseButton>
);

/**
 * 갤러리 하단의 나누기 진행 바와 결과 알림.
 * 상태는 갖지 않는다. 무엇을 그릴지는 `job.status`·`job.kind`·`job.groupCount` 가 정한다.
 */
export const OrganizeStatusBar = ({
  job,
  onCancel,
  onReview,
  onRetry,
  onDismiss,
}: OrganizeStatusBarProps) => {
  const copy = COPY[job.kind];

  if (job.status === "QUEUED" || job.status === "RUNNING") {
    const percent = job.status === "RUNNING" ? job.percent : null;
    const label = job.status === "QUEUED" ? "분석 대기 중..." : runningLabelOf(job.kind, percent);

    return (
      <Dock>
        <FloatingBar>
          <Count>{countOf(job)}</Count>
          <Status
            variant="soft"
            role="progressbar"
            aria-label={copy.progressLabel}
            aria-valuetext={label}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent ?? undefined}
          >
            <Fill style={{ width: `${percent ?? 0}%` }} />
            <StatusText>
              <Spinner src={spinnerIcon} alt="" width={14} height={14} />
              {label}
            </StatusText>
          </Status>
          <CancelButton type="button" onClick={onCancel}>
            취소
          </CancelButton>
        </FloatingBar>
      </Dock>
    );
  }

  if (job.status === "READY" && job.groupCount !== 0) {
    return (
      <Dock>
        <FloatingBar>
          <SuccessIcon />
          <Message $size={14} role="status">
            {copy.ready(job.groupCount)}
          </Message>
          <ActionButton type="button" onClick={onReview}>
            확인하기
          </ActionButton>
          <Close onClick={onDismiss} />
        </FloatingBar>
      </Dock>
    );
  }

  const isFailed = job.status === "FAILED";

  return (
    <Dock>
      <FloatingBar>
        <AlertIcon />
        <Message $size={12} role="status">
          {isFailed ? copy.failed : copy.empty}
        </Message>
        {isFailed && job.retryable && (
          <ActionButton type="button" onClick={onRetry}>
            다시 시도
          </ActionButton>
        )}
        <Close onClick={onDismiss} />
      </FloatingBar>
    </Dock>
  );
};
