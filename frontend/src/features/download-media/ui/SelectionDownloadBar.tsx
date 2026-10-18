import { useEffect, useRef, useState } from "react";
import {
  LuCheck,
  LuDownload,
  LuFolderInput,
  LuImageDown,
  LuLoaderCircle,
  LuTrash2,
  LuX,
} from "react-icons/lu";

import { type AnalyticsRoomContext, getAnalyticsRoom } from "@/shared/lib";
import { FloatingBar } from "@/shared/ui/floating-bar";
import { IconButton } from "@/shared/ui/icon-button";
import { Toast } from "@/shared/ui/toast";
import { downloadMessageOfError } from "../lib/downloadErrorMessage";
import { useDownloadFailure } from "../model/useDownloadFailure";
import { trackDownload, trackShareBatch } from "../model/trackDownload";
import { type PendingShareSnapshot, useMediaDownload } from "../model/useMediaDownload";
import type { DownloadPhase } from "../model/downloadProgress";
import type { DownloadMode, DownloadOutcome, DownloadTarget } from "../model/types";
import { DiscardShareModal } from "./DiscardShareModal";
import { DownloadFailureModal } from "./DownloadFailureModal";
import { DownloadModeSheet } from "./DownloadModeSheet";
import {
  ActionGroup,
  Count,
  DownloadButton,
  Dock,
  Fill,
  PlainButton,
  SelectionCheck,
  SelectionLayout,
  SelectionSummary,
  ShareButton,
  Status,
  StatusText,
} from "./SelectionDownloadBar.styles";

/**
 * 사진을 하나라도 고르면 나타나는 바 (003-selection-download).
 * 아무것도 안 골랐으면 **아무것도 그리지 않는다** — 평소에 화면을 차지하지 않기 위해서다.
 *
 * 지금은 받기만 있다. 삭제·폴더 이동은 각자 서버 API 가 생긴 뒤에 이 바로 들어오고,
 * 그때 이 컴포넌트는 `widgets/selection-actions` 로 올라간다 — 기능 슬라이스 두 개를
 * 합치는 자리는 위젯이지, 그중 한 기능의 안쪽이 아니다.
 */

interface SelectionDownloadBarProps {
  /** 지금 고른 미디어. 순서가 그대로 파일 이름 순서가 된다. */
  targets: DownloadTarget[];
  roomId: number;
  /** 시트에서 zip 파일명을 미리 보여주는 데 쓴다. */
  roomCode: string;
  /** 방 전체 장수. 전부 골라 받았는지 가려내는 데 쓴다. */
  roomPhotoCount: number;
  /** 지금 선택이 "전체 선택" 으로 고른 것인지 */
  isAllSelected?: boolean;
  token: string;
  onClearSelection: () => void;
  onDeleteSelection?: () => void;
  onMoveSelection?: () => void;
  /** 한 판이 끝났을 때. 실패 안내는 부르는 쪽이 띄운다. */
  onSettled?: (outcome: DownloadOutcome) => void;
}

/**
 * 단계마다 사용자가 기다리는 것이 다르다. 회선을 기다리는지, 서버가 묶기를 기다리는지,
 * 사진첩으로 넘어가기를 기다리는지 — 같은 스피너라도 무엇을 기다리는지는 말해줘야 한다.
 */
const STATUS_TEXT: Record<DownloadPhase, string> = {
  fetching: "다운로드 중",
  zipping: "압축 중",
  receiving: "zip 받는 중",
  sharing: "사진첩에 저장하는 중",
};

/**
 * 사진첩 저장을 마쳤다는 토스트가 떠 있는 시간.
 * 결과를 알리기만 하고 할 일은 없는 알림이라, 닫기 버튼 없이 이 시간 뒤에 스스로 사라진다.
 */
const SHARE_DONE_TOAST_MS = 2000;

/**
 * 받아둔 것을 넘기는 바의 왼쪽 글자와 버튼 글자.
 *
 * 한 묶음이라도 넘긴 뒤에는 "왜 또 눌러야 하는지" 가 보여야 한다 — 저장한 장수와 남은 장수를
 * 함께 말하고, 버튼도 같은 동작의 반복이 아니라 이어지는 다음 단계로 읽히게 쓴다.
 */
const shareBarTextOf = ({ nextCount, savedCount, remainingCount }: PendingShareSnapshot) =>
  savedCount === 0
    ? {
        summary: `${remainingCount}장 준비됨`,
        action: "사진첩에 저장",
      }
    : {
        summary: `${savedCount}장 저장 / ${remainingCount}장 남음`,
        action: `다음 ${nextCount}장 저장`,
      };

export const SelectionDownloadBar = ({
  targets,
  roomId,
  roomCode,
  roomPhotoCount,
  isAllSelected = false,
  token,
  onClearSelection,
  onDeleteSelection,
  onMoveSelection,
  onSettled,
}: SelectionDownloadBarProps) => {
  const [isSheetOpen, setSheetOpen] = useState(false);
  const {
    failure,
    settle: settleFailure,
    fail: failWith,
    close: closeFailure,
  } = useDownloadFailure();
  /**
   * 방금 굴린 판이 무엇이었는지. 재시도가 같은 방식으로 다시 받아야 하는데,
   * 그때는 이미 선택이 풀렸을 수도 있어 `targets` 만으로는 되짚을 수 없다.
   */
  const [lastRun, setLastRun] = useState<{ targets: DownloadTarget[]; mode: DownloadMode }>({
    targets: [],
    mode: "individual",
  });
  /**
   * 이벤트에 남길 이번 판. `lastRun` 은 상태라 `start` 를 부른 렌더의 콜백에서는
   * 아직 이전 판을 가리킨다 — 그대로 쓰면 첫 판이 빈 판으로, 이후 판이 한 판씩 밀려 기록된다.
   */
  const trackedRunRef = useRef<{
    targets: DownloadTarget[];
    mode: DownloadMode;
    isSelectAll: boolean;
    room: AnalyticsRoomContext | null;
  }>({ targets: [], mode: "individual", isSelectAll: false, room: null });
  const trackRun = (outcome: DownloadOutcome) =>
    trackDownload(outcome, {
      mode: trackedRunRef.current.mode,
      source: "gallery",
      selectedCount: trackedRunRef.current.targets.length,
      roomPhotoCount,
      isSelectAll: trackedRunRef.current.isSelectAll,
      room: trackedRunRef.current.room,
    });
  const [isDiscardOpen, setDiscardOpen] = useState(false);
  /** 사진첩 저장을 모두 마친 장수. 차 있는 동안 완료 토스트를 띄운다. */
  const [sharedDoneCount, setSharedDoneCount] = useState<number | null>(null);

  useEffect(() => {
    if (sharedDoneCount === null) {
      return;
    }

    const timer = window.setTimeout(() => setSharedDoneCount(null), SHARE_DONE_TOAST_MS);

    return () => window.clearTimeout(timer);
  }, [sharedDoneCount]);

  const { progress, pendingShare, isSharing, start, share, dismissShare, cancel } =
    useMediaDownload({
      roomId,
      token,
      onSettled: (outcome) => {
        settleFailure(outcome, lastRun.targets, lastRun.mode);
        trackRun(outcome);

        // 묶음 사이에는 바가 남은 장수를 말한다. 토스트는 다 끝났을 때 한 번만 띄운다.
        // 못 받은 것이 섞였으면 실패 모달이 그 판을 설명하므로 겹쳐 띄우지 않는다.
        if (
          trackedRunRef.current.mode === "share" &&
          outcome.type === "saved" &&
          outcome.failed.length === 0
        ) {
          setSharedDoneCount(outcome.savedCount);
        }

        // 저장까지 끝났으면 고른 상태를 풀어준다. 실패가 섞였거나 탭이 한 번 더 필요하면
        // 그대로 둔다 — 다시 시도할 대상을 사용자가 다시 고르게 만들면 안 된다.
        if (outcome.type === "saved" && outcome.failed.length === 0) {
          onClearSelection();
        }

        onSettled?.(outcome);
      },
      // 결말을 만들지도 못하고 튄 예외. 여기서 잡지 않으면 실패가 조용히 사라진다.
      onError: (error) => {
        const reason = downloadMessageOfError(error);
        trackRun({ type: "failed", reason, isRetryable: false });
        failWith(reason, lastRun.targets, lastRun.mode);
      },
      onShareBatch: (batch) => trackShareBatch(batch, trackedRunRef.current.room),
    });

  const startWith = (mode: DownloadMode, only: DownloadTarget[] = targets) => {
    setSheetOpen(false);
    closeFailure();
    setLastRun({ targets: only, mode });
    trackedRunRef.current = {
      targets: only,
      mode,
      // 실패한 것만 다시 받는 판은 전체 선택으로 고른 판이 아니다
      isSelectAll: isAllSelected && only === targets,
      room: getAnalyticsRoom(),
    };
    void start(only, mode);
  };

  // 받는 중도, 보낼 것도, 고른 것도 없다. 바가 있을 이유가 없다.
  // 실패 모달은 별개다 — 선택이 풀린 뒤에도 왜 못 받았는지는 말해줘야 한다.
  const isBarHidden = progress === null && pendingShare === null && targets.length === 0;

  return (
    <>
      {!isBarHidden && (
        <Dock>
          <FloatingBar>
            {progress !== null ? (
              <>
                {/*
                  장수는 우리가 한 장씩 받을 때만 오른다. 압축은 서버가 하므로 완료 장수를 알 수 없고,
                  그때 "0 / 3" 을 띄우면 멈춘 것으로 읽힌다 — 대상 장수만 보여준다.
                */}
                <Count>
                  {progress.phase === "fetching"
                    ? `${progress.completedCount} / ${progress.totalCount}`
                    : `${progress.totalCount}장`}
                </Count>
                {/*
                  공유 시트로 넘기는 순간은 길이를 알 수 없다 — 그 구간만 퍼센트도 채움도 없이
                  돌아가는 스피너로 둔다. 나머지 구간은 값이 실제로 움직이므로 업로드 바와
                  똑같이 숫자와 띠를 함께 보여준다.
                */}
                <Status
                  role="progressbar"
                  aria-label="다운로드 진행률"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={progress.phase === "sharing" ? undefined : progress.percent}
                >
                  {progress.phase !== "sharing" && (
                    <Fill style={{ width: `${progress.percent}%` }} />
                  )}
                  <StatusText>
                    <LuLoaderCircle className="spin" />
                    {STATUS_TEXT[progress.phase]}...
                    {progress.phase !== "sharing" && ` ${progress.percent}%`}
                  </StatusText>
                </Status>
                <PlainButton type="button" onClick={cancel}>
                  취소
                </PlainButton>
              </>
            ) : pendingShare !== null ? (
              // 고르기 바와 같은 자리에 놓는다 — 장수는 왼쪽, 버튼은 가운데, 닫기는 오른쪽.
              <SelectionLayout>
                <SelectionSummary>
                  <Count>{shareBarTextOf(pendingShare).summary}</Count>
                </SelectionSummary>
                {/*
                  여기서만은 핸들러가 곧바로 시트를 연다. 사이에 await 가 끼면 사파리가 막는다.
                  누른 뒤 `share()` 가 끝날 때까지는 잠근다. 시트가 뜨기 전 몇 초가 비어 보이고,
                  그 사이 다시 누르면 사파리가 앞선 공유가 안 끝났다며 거절한다.
                */}
                <ShareButton type="button" disabled={isSharing} onClick={() => void share()}>
                  {isSharing ? <LuLoaderCircle className="spin" /> : <LuImageDown />}
                  {isSharing ? "사진첩에 저장하는 중..." : shareBarTextOf(pendingShare).action}
                </ShareButton>
                <ActionGroup>
                  <PlainButton
                    type="button"
                    aria-label="받아둔 사진 버리기"
                    disabled={isSharing}
                    onClick={() => setDiscardOpen(true)}
                  >
                    <LuX />
                  </PlainButton>
                </ActionGroup>
              </SelectionLayout>
            ) : (
              <SelectionLayout>
                <SelectionSummary>
                  <SelectionCheck
                    type="button"
                    aria-label="선택 모두 해제"
                    onClick={onClearSelection}
                  >
                    <LuCheck />
                  </SelectionCheck>
                  <Count>{targets.length}개</Count>
                </SelectionSummary>
                <DownloadButton type="button" onClick={() => setSheetOpen(true)}>
                  <LuDownload />
                  다운로드
                </DownloadButton>
                <ActionGroup>
                  <IconButton
                    size="sm"
                    variant="danger"
                    aria-label="선택한 사진 삭제"
                    onClick={onDeleteSelection}
                  >
                    <LuTrash2 />
                  </IconButton>
                  <IconButton
                    size="sm"
                    aria-label="선택한 사진 폴더 이동"
                    onClick={onMoveSelection}
                  >
                    <LuFolderInput />
                  </IconButton>
                </ActionGroup>
              </SelectionLayout>
            )}
          </FloatingBar>
        </Dock>
      )}

      {isSheetOpen && (
        <DownloadModeSheet
          count={targets.length}
          roomCode={roomCode}
          onSubmit={(mode) => startWith(mode)}
          onClose={() => setSheetOpen(false)}
        />
      )}

      {failure !== null && (
        <DownloadFailureModal
          count={failure.count}
          message={failure.message}
          // 다시 받아볼 것이 하나도 없으면 재시도를 내주지 않는다 (`useDownloadFailure` 참고).
          isRetryable={failure.targets.length > 0}
          onRetry={() => startWith(failure.mode, failure.targets)}
          onClose={closeFailure}
        />
      )}

      {isDiscardOpen && pendingShare !== null && (
        <DiscardShareModal
          remainingCount={pendingShare.remainingCount}
          savedCount={pendingShare.savedCount}
          onDiscard={() => {
            setDiscardOpen(false);
            dismissShare();
          }}
          onClose={() => setDiscardOpen(false)}
        />
      )}

      {sharedDoneCount !== null && (
        <Toast
          message={`${sharedDoneCount}장을 사진첩에 저장했어요.`}
          showClose={false}
          onClose={() => setSharedDoneCount(null)}
        />
      )}
    </>
  );
};
