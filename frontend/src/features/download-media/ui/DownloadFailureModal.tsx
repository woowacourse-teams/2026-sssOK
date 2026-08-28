import mascotCrying from "@/shared/assets/mascot-crying.png";
import { Modal } from "@/shared/ui/modal";
import {
  Actions,
  CloseAction,
  Content,
  Description,
  Mascot,
  RetryAction,
  Title,
} from "./DownloadFailureModal.styles";

export interface DownloadFailureModalProps {
  /**
   * 못 받은 장수. **0 이면 판 전체가 무너진 것이다** — 압축 잡을 만들지도 못한 경우라
   * 셀 장수가 없다. 그때는 제목이 장수를 말하지 않는다.
   */
  count: number;
  /** 왜 못 받았는지 한 줄. 상태 코드를 `downloadErrorMessage` 가 옮긴 문장이다. */
  message: string;
  /**
   * 재시도를 내줄지. **없는 사진(404)만 걸러진다** — 다시 눌러도 결과가 같은 실패에
   * 버튼을 내주면 사용자가 같은 벽에 반복해서 부딪힌다.
   */
  isRetryable: boolean;
  onRetry: () => void;
  onClose: () => void;
}

/**
 * 받기가 끝났는데 못 받은 것이 있으면 뜨는 모달 (#120 · #121).
 *
 * 업로드 실패 모달과 달리 **사유를 말한다.** 받기는 사용자가 고칠 수 있는 실패가 섞여
 * 있어서다 — "아직 처리 중이에요" 는 잠시 뒤에 다시 누르라는 뜻이고, "찾을 수 없어요" 는
 * 기다려도 소용없다는 뜻이다. 둘을 같은 문장으로 덮으면 사용자가 어느 쪽인지 알 수 없다.
 *
 * 띄울지 말지는 정하지 않는다 — 부르는 쪽이 렌더링하지 않는 것으로 닫는다.
 */
export const DownloadFailureModal = ({
  count,
  message,
  isRetryable,
  onRetry,
  onClose,
}: DownloadFailureModalProps) => {
  return (
    <Modal onClose={onClose}>
      <Content>
        <Mascot src={mascotCrying} alt="" />
        <Title>{count > 0 ? `앗, ${count}장을 못 받았어요` : "앗, 받지 못했어요"}</Title>
        <Description>{message}</Description>
        <Actions>
          <CloseAction variant="default" onClick={onClose}>
            닫기
          </CloseAction>
          {isRetryable && (
            <RetryAction variant="primary" onClick={onRetry}>
              재시도
            </RetryAction>
          )}
        </Actions>
      </Content>
    </Modal>
  );
};
