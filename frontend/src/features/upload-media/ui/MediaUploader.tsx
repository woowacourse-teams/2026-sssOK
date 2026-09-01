import { useEffect, useState } from "react";

import { Toast } from "@/shared/ui/toast";
import { uploadErrorNoticeOf } from "../lib/uploadErrorNotice";
import type { MediaSelection, RejectedSelection } from "../model/selectMediaFiles";
import { useMediaUpload } from "../model/useMediaUpload";
import { useUploadFailure } from "../model/useUploadFailure";
import { RejectedFilesModal } from "./RejectedFilesModal";
import { UploadButton } from "./UploadButton";
import { UploadFailureModal } from "./UploadFailureModal";
import { UploadProgressBar } from "./UploadProgressBar";

/** 토스트가 스스로 사라지기까지. 공유 링크 복사 알림과 같은 시간을 쓴다. */
const NOTICE_DURATION_MS = 4000;

export interface MediaUploaderProps {
  roomId: number;
  token: string;
  /**
   * 올릴 수 있는 사람인지. 방장만 올리는 방의 참여자는 거짓이다 (`canUploadTo`).
   *
   * 거짓이면 버튼도 선택기도 내주지 않는다 — 서버가 발급에서 403(`UPLOAD_NOT_ALLOWED`)으로
   * 막을 것이라, 누를 수 있게 두면 실패하는 길만 안내하는 셈이다.
   *
   * 기본값을 두지 않는 이유는, 빠뜨렸을 때 조용히 "올릴 수 있다" 로 굳어지기 때문이다.
   */
  canUpload: boolean;
  /** 갤러리 선택 바가 표시될 때 플로팅 업로드 버튼을 숨긴다. */
  hideButton?: boolean;
  /** 지금 열어둔 폴더. 없으면 루트로 올라간다. */
  folderIds?: number[];
  /**
   * 한 장이라도 등록까지 끝났을 때. 목록을 다시 불러오라는 뜻이다.
   *
   * 갤러리를 여기서 직접 건드리지 않는 이유는, 올린 사진이 어디에 어떻게 보여야 하는지가
   * 부르는 화면마다 다르기 때문이다. 실패분이 남아 모달이 떠 있어도 이건 먼저 불린다 —
   * 올라간 것은 올라간 것이다.
   */
  onUploaded?: () => void;
  /**
   * 이 방에 더 있을 수 없을 때. 방이 사라졌거나(410·404) 세션이 죽었다(401).
   *
   * 여기서 직접 이동하지 않는 이유는 라우팅이 화면마다 다르기 때문이다. 부르는 쪽은
   * **들고 있던 방 정보를 버리고** 입장 화면으로 보내야 한다 — 캐시가 남아 있으면
   * 입장 화면이 아직 멀쩡한 방인 줄 알고 곧장 되돌려보낸다.
   */
  onLeaveRoom?: () => void;
}

/**
 * 업로드 진입점. 고르기 → 전송 → 진행 바 → 실패 모달까지 한 흐름을 잇는다.
 *
 * 상태를 직접 만들지 않는다 — 진행은 `useMediaUpload`, 실패 모달은 `useUploadFailure` 가 들고
 * 있고 여기는 둘을 이어 붙이기만 한다. 재시도가 그 이음매다: 실패분을 그대로 다시 한 판
 * 굴리는 것이라, 끝나면 같은 `onSettled` 로 돌아와 또 깨진 게 있으면 모달이 다시 뜬다.
 */
export const MediaUploader = ({
  roomId,
  token,
  canUpload,
  folderIds,
  onUploaded,
  onLeaveRoom,
  hideButton = false,
}: MediaUploaderProps) => {
  const [rejected, setRejected] = useState<RejectedSelection[]>([]);
  /** 배치 전체가 못 올라간 이유. 진행 바가 사라진 자리에 이게 대신 뜬다 (#148). */
  const [notice, setNotice] = useState<string | null>(null);
  const failure = useUploadFailure();
  /*
   * 발급이 거절한 파일(`onRejected`)은 아직 잇지 않았다 — "고른 장수" 를 서버 응답으로
   * 정정하는 문제라, 실패 모달(#74)의 몫이 아니다.
   */
  const upload = useMediaUpload({
    roomId,
    token,
    folderIds,
    onSettled: (result, { superseded }) => {
      // `alreadyRegistered` 도 서버에 올라가 있는 것이다. 등록 응답에 Media 가 없어
      // `registered` 에 못 담길 뿐이라, 이것만 온 판도 목록을 다시 불러와야 나타난다.
      if (result.registered.length > 0 || result.alreadyRegistered > 0) {
        onUploaded?.();
      }

      // 취소했거나 새 판이 시작된 뒤에 끝난 판이다. 지금 떠 있는 모달을 지우면 안 된다.
      if (!superseded) {
        failure.settle(result);
      }
    },
    /*
     * 배치 전체가 못 올라갔다 (#148). 여기 오는 건 파일이 깨진 게 아니라 방·권한·회선
     * 문제라, 파일 이름을 늘어놓는 실패 모달로는 할 말이 없다 — 한 줄로 이유만 말한다.
     *
     * 진행 바는 이미 `useMediaUpload` 가 치웠다. 그 자리를 이게 대신해야 "잠깐 떴다
     * 사라지는" 화면이 되지 않는다.
     */
    onError: (error) => {
      const { message, leavesRoom } = uploadErrorNoticeOf(error);

      // 곧 화면이 바뀐다. 토스트를 띄워봐야 이동하면서 같이 사라진다.
      if (leavesRoom) {
        onLeaveRoom?.();
        return;
      }

      setNotice(message);
    },
  });

  // 손대지 않아도 사라진다. 알림 하나 때문에 닫기를 누르게 만들지 않는다.
  useEffect(() => {
    if (notice === null) return;

    const timer = window.setTimeout(() => setNotice(null), NOTICE_DURATION_MS);

    return () => window.clearTimeout(timer);
  }, [notice]);

  /**
   * 올릴 수 없는 사람의 파일은 한 장도 내보내지 않는다.
   *
   * 버튼도 재시도도 이미 `canUpload` 로 가려져 있어서, 여기까지 오는 길은 남아 있지 않다.
   * 그래도 한 번 더 보는 건 **고르기와 재시도가 둘 다 이리로 모이기 때문**이다 —
   * 나중에 세 번째 길이 붙어도 서버까지 가서 403 을 받아오지는 않는다.
   */
  const start = (files: File[]) => {
    if (!canUpload) return;

    // 지난 판의 이유를 지운다. 새로 올리는 중에 옛 실패가 떠 있으면 이 판의 말로 읽힌다.
    setNotice(null);
    upload.start(files);
  };

  /**
   * 못 올리는 것만 모달로 알린다 (시안 07d).
   *
   * 올라갈 장수는 진행 바가 `0 / 8` 로 말하고 있어서, 그 위에 "8장을 선택했어요" 를 띄우면
   * 같은 말이 두 번 뜬다. 반대로 걸러진 파일은 애초에 업로드에 끼지 않아서 진행 바가
   * 대신 말해주지 못한다 — 그것만 알려야 사용자가 장수가 줄어든 이유를 안다.
   *
   * **모달을 띄우면서 통과한 것은 이미 올라가기 시작한다.** 걸러진 파일은 사용자가
   * 결정할 것이 없어서(다시 눌러도 거절된다) 확인을 기다릴 이유가 없고, 기다리게 하면
   * 멀쩡한 27장이 모달을 닫을 때까지 멈춰 선다.
   */
  const handleSelect = (next: MediaSelection) => {
    setRejected(next.rejected);
    start(next.accepted);
  };

  const handleRetry = () => {
    const targets = failure.files;

    // 모달을 먼저 치운다. 그 자리를 진행 바가 대신한다 — 첫 업로드 때와 같은 화면이다.
    failure.close();
    start(targets);
  };

  return (
    <>
      {/*
        올릴 수 없으면 버튼째로 빼서 선택기까지 함께 사라진다 (`hidden` 과 다른 점이다 —
        그건 선택 바에 자리를 내주는 것이라 입력은 남겨둔다).

        진행 바와 실패 모달은 이 조건 밖에 둔다. 올리는 도중에 권한을 잃더라도 지금 올라가는
        판은 계속 굴러가고 있고, 그 화면을 지우면 이 이슈가 고치려던 "말없이 사라지는 진행 바"
        를 여기서 다시 만들게 된다.
      */}
      {canUpload && (
        <UploadButton onSelect={handleSelect} hidden={hideButton || upload.progress !== null} />
      )}
      {upload.progress !== null && (
        <UploadProgressBar
          completedCount={upload.progress.completedCount}
          totalCount={upload.progress.totalCount}
          percent={upload.progress.percent}
          onCancel={upload.cancel}
        />
      )}
      {rejected.length > 0 && (
        <RejectedFilesModal rejected={rejected} onClose={() => setRejected([])} />
      )}
      {notice !== null && <Toast tone="error" message={notice} onClose={() => setNotice(null)} />}
      {failure.isOpen && (
        <UploadFailureModal
          failures={failure.failures}
          // 올릴 권한을 잃은 뒤라면 재시도를 내주지 않는다. 남겨두면 눌러도 아무 일이
          // 없거나, 서버까지 가서 403 으로 되돌아온다.
          onRetry={canUpload ? handleRetry : undefined}
          onClose={failure.close}
        />
      )}
    </>
  );
};
