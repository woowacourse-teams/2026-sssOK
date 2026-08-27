import { useState } from "react";
import { LuDownload, LuFileArchive, LuLoaderCircle, LuX } from "react-icons/lu";

import { FloatingBar } from "@/shared/ui/floating-bar";
import { downloadMessageOfError } from "../lib/downloadErrorMessage";
import { useDownloadFailure } from "../model/useDownloadFailure";
import { useMediaDownload } from "../model/useMediaDownload";
import type { DownloadMode, DownloadOutcome, DownloadTarget } from "../model/types";
import { DownloadFailureModal } from "./DownloadFailureModal";
import { Count, DownloadButton, Dock, PlainButton, Status } from "./SelectionDownloadBar.styles";

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
  token: string;
  onClearSelection: () => void;
  /** 한 판이 끝났을 때. 실패 안내는 부르는 쪽이 띄운다. */
  onSettled?: (outcome: DownloadOutcome) => void;
}

/**
 * 단계마다 사용자가 기다리는 것이 다르다. 회선을 기다리는지 서버가 묶기를 기다리는지 —
 * 같은 스피너라도 무엇을 기다리는지는 말해줘야 한다.
 */
const STATUS_TEXT: Record<string, string> = {
  fetching: "다운로드 중",
  zipping: "압축 중",
};

export const SelectionDownloadBar = ({
  targets,
  roomId,
  token,
  onClearSelection,
  onSettled,
}: SelectionDownloadBarProps) => {
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
  const { progress, start, cancel } = useMediaDownload({
    roomId,
    token,
    onSettled: (outcome) => {
      settleFailure(outcome, lastRun.targets, lastRun.mode);

      // 저장까지 끝났으면 고른 상태를 풀어준다. 실패가 섞였으면 그대로 둔다 —
      // 다시 시도할 대상을 사용자가 다시 고르게 만들면 안 된다.
      if (outcome.type === "saved" && outcome.failed.length === 0) {
        onClearSelection();
      }

      onSettled?.(outcome);
    },
    // 결말을 만들지도 못하고 튄 예외. 여기서 잡지 않으면 실패가 조용히 사라진다.
    onError: (error) => failWith(downloadMessageOfError(error), lastRun.targets, lastRun.mode),
  });

  const startWith = (mode: DownloadMode, only: DownloadTarget[] = targets) => {
    closeFailure();
    setLastRun({ targets: only, mode });
    void start(only, mode);
  };

  // 받는 중도 아니고 고른 것도 없다. 바가 있을 이유가 없다.
  // 실패 모달은 별개다 — 선택이 풀린 뒤에도 왜 못 받았는지는 말해줘야 한다.
  const isBarHidden = progress === null && targets.length === 0;

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
                <Status>
                  <LuLoaderCircle className="spin" />
                  {STATUS_TEXT[progress.phase]}... {progress.percent}%
                </Status>
                <PlainButton type="button" onClick={cancel}>
                  취소
                </PlainButton>
              </>
            ) : (
              <>
                <Count>선택 {targets.length}개</Count>
                <PlainButton type="button" aria-label="선택 해제" onClick={onClearSelection}>
                  <LuX />
                </PlainButton>
                {/*
              두 방식을 나란히 내준다. 어느 쪽을 고를지는 사용자 몫이다 — 장수로 자동 판단하지 않는다.
              고르는 자리를 시트로 옮기고 폰에서 "사진첩에 저장"을 더하는 것은 #122·#123 이다.
            */}
                <PlainButton type="button" onClick={() => startWith("individual")}>
                  <LuDownload />
                  개별 저장
                </PlainButton>
                <DownloadButton type="button" onClick={() => startWith("zip")}>
                  <LuFileArchive />
                  zip 다운로드
                </DownloadButton>
              </>
            )}
          </FloatingBar>
        </Dock>
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
