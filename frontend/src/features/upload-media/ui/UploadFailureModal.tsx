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
} from "./UploadFailureModal.styles";

export interface UploadFailureModalProps {
  /**
   * 못 올린 장수. **재시도가 실제로 다시 올릴 장수와 같아야 한다** —
   * `retryableFilesOf` 가 추린 목록의 길이를 그대로 넘긴다.
   */
  count: number;
  onRetry: () => void;
  onClose: () => void;
}

/**
 * 업로드가 끝났을 때 깨진 파일이 있으면 뜨는 모달 (#74).
 *
 * 띄울지 말지는 정하지 않는다 — 부르는 쪽이 렌더링하지 않는 것으로 닫는다.
 * 사유를 나누지도 않는다. 여기 오는 건 전부 "다시 올리면 될 수도 있는" 실패라서
 * 사용자가 고를 것이 재시도냐 닫기냐 둘뿐이다. 파일 자체가 조건에 안 맞는 것(`rejected`)은
 * 애초에 이 모달에 오지 않는다.
 */
export const UploadFailureModal = ({ count, onRetry, onClose }: UploadFailureModalProps) => {
  return (
    <Modal onClose={onClose}>
      <Content>
        {/* 장수는 제목이 말한다. 여기서 또 읽어주면 같은 말을 두 번 듣는다. */}
        <Mascot src={mascotCrying} alt="" />
        <Title>앗, {count}장을 못올렸어요</Title>
        <Description>
          예기치 못한 이유로 업로드에 실패했어요
          <br />
          다시 시도해주세요.
        </Description>
        <Actions>
          <CloseAction variant="default" onClick={onClose}>
            닫기
          </CloseAction>
          <RetryAction variant="primary" onClick={onRetry}>
            재시도
          </RetryAction>
        </Actions>
      </Content>
    </Modal>
  );
};
