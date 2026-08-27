import { useState } from "react";
import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";
import type { MediaSelection, RejectedSelection } from "../model/selectMediaFiles";
import { useMediaUpload } from "../model/useMediaUpload";
import { useUploadFailure } from "../model/useUploadFailure";
import { RejectedFilesModal } from "./RejectedFilesModal";
import { UploadButton } from "./UploadButton";
import { UploadFailureModal } from "./UploadFailureModal";
import { UploadProgressBar } from "./UploadProgressBar";

export interface MediaUploaderProps {
  roomId: number;
  token: string;
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
}

/**
 * 업로드 진입점. 고르기 → 전송 → 진행 바 → 실패 모달까지 한 흐름을 잇는다.
 *
 * 상태를 직접 만들지 않는다 — 진행은 `useMediaUpload`, 실패 모달은 `useUploadFailure` 가 들고
 * 있고 여기는 둘을 이어 붙이기만 한다. 재시도가 그 이음매다: 실패분을 그대로 다시 한 판
 * 굴리는 것이라, 끝나면 같은 `onSettled` 로 돌아와 또 깨진 게 있으면 모달이 다시 뜬다.
 */
export const MediaUploader = ({ roomId, token, folderIds, onUploaded }: MediaUploaderProps) => {
  const [rejected, setRejected] = useState<RejectedSelection[]>([]);
  const failure = useUploadFailure();
  /*
   * 발급이 거절한 파일(`onRejected`)과 배치 전체가 못 올라간 경우(`onError`, 403·410)는
   * 아직 잇지 않았다. 앞은 "고른 장수" 를 서버 응답으로 정정하는 문제고, 뒤는 만료·권한이라
   * 입장 화면으로 되돌리는 라우팅 결정이 필요하다 — 둘 다 실패 모달(#74)의 몫이 아니다.
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
  });

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
    upload.start(next.accepted);
  };

  const handleRetry = () => {
    const targets = failure.files;

    // 모달을 먼저 치운다. 그 자리를 진행 바가 대신한다 — 첫 업로드 때와 같은 화면이다.
    failure.close();
    upload.start(targets);
  };

  return (
    <Stack>
      <UploadButton onSelect={handleSelect} />
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
      {failure.isOpen && (
        <UploadFailureModal count={failure.count} onRetry={handleRetry} onClose={failure.close} />
      )}
    </Stack>
  );
};

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 0 ${spacing[16]};
`;
