import { useState } from "react";
import {
  LuCheck,
  LuDownload,
  LuFolderInput,
  LuImageDown,
  LuLoaderCircle,
  LuTrash2,
  LuX,
} from "react-icons/lu";

import { FloatingBar } from "@/shared/ui/floating-bar";
import { IconButton } from "@/shared/ui/icon-button";
import { downloadMessageOfError } from "../lib/downloadErrorMessage";
import { useDownloadFailure } from "../model/useDownloadFailure";
import { useMediaDownload } from "../model/useMediaDownload";
import type { DownloadPhase } from "../model/downloadProgress";
import type { DownloadMode, DownloadOutcome, DownloadTarget } from "../model/types";
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
  sharing: "사진첩으로 보내는 중",
};

export const SelectionDownloadBar = ({
  targets,
  roomId,
  roomCode,
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
  const { progress, pendingShare, start, share, dismissShare, cancel } = useMediaDownload({
    roomId,
    token,
    onSettled: (outcome) => {
      settleFailure(outcome, lastRun.targets, lastRun.mode);

      // 저장까지 끝났으면 고른 상태를 풀어준다. 실패가 섞였거나 탭이 한 번 더 필요하면
      // 그대로 둔다 — 다시 시도할 대상을 사용자가 다시 고르게 만들면 안 된다.
      if (outcome.type === "saved" && outcome.failed.length === 0) {
        onClearSelection();
      }

      onSettled?.(outcome);
    },
    // 결말을 만들지도 못하고 튄 예외. 여기서 잡지 않으면 실패가 조용히 사라진다.
    onError: (error) => failWith(downloadMessageOfError(error), lastRun.targets, lastRun.mode),
  });

  const startWith = (mode: DownloadMode, only: DownloadTarget[] = targets) => {
    setSheetOpen(false);
    closeFailure();
    setLastRun({ targets: only, mode });
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
              <>
                <Count>{pendingShare.length}장 준비됨</Count>
                {/* 여기서만은 핸들러가 곧바로 시트를 연다. 사이에 await 가 끼면 사파리가 막는다. */}
                <DownloadButton type="button" onClick={() => void share()}>
                  <LuImageDown />
                  사진첩에 저장
                </DownloadButton>
                <PlainButton type="button" aria-label="받아둔 사진 버리기" onClick={dismissShare}>
                  <LuX />
                </PlainButton>
              </>
            ) : (
              <SelectionLayout>
                <SelectionSummary>
                  <SelectionCheck aria-hidden="true">
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
    </>
  );
};
