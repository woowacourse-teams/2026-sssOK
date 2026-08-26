import { useState } from "react";
import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";
import type { MediaSelection } from "../model/selectMediaFiles";
import { useMediaUpload } from "../model/useMediaUpload";
import { useUploadFailure } from "../model/useUploadFailure";
import { SelectionNotice } from "./SelectionNotice";
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
  const [selection, setSelection] = useState<MediaSelection | null>(null);
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
    onSettled: (result) => {
      if (result.registered.length > 0) {
        onUploaded?.();
      }

      failure.settle(result);
    },
  });

  const handleSelect = (next: MediaSelection) => {
    setSelection(next);

    if (next.accepted.length > 0) {
      upload.start(next.accepted);
    }
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
      <LiveRegion role="status" aria-live="polite">
        {selection !== null && (
          <SelectionNotice selection={selection} onDismiss={() => setSelection(null)} />
        )}
      </LiveRegion>
      {upload.progress !== null && (
        <UploadProgressBar
          completedCount={upload.progress.completedCount}
          totalCount={upload.progress.totalCount}
          percent={upload.progress.percent}
          onCancel={upload.cancel}
        />
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

/**
 * 알림이 뜨기 **전부터** DOM 에 있어야 스크린리더가 읽어준다.
 * 영역과 내용이 같은 순간에 생기면 변화로 잡히지 않아 그냥 지나간다.
 *
 * 그래서 비어 있을 때 자리를 차지하면 안 되고, 위 간격도 `Stack` 의 gap 이 아니라
 * 안쪽 알림이 들고 있어야 한다 — gap 은 빈 영역에도 붙는다.
 */
const LiveRegion = styled.div`
  width: 100%;

  > * {
    margin-top: ${spacing[12]};
  }
`;
