import { Modal } from "@/shared/ui/modal";
import {
  Actions,
  CloseAction,
  Content,
  Description,
  RetryAction as KeepAction,
  Title,
} from "./DownloadFailureModal.styles";

export interface DiscardShareModalProps {
  /** 아직 공유 시트로 넘기지 않은 장수 */
  remainingCount: number;
  /** 이미 넘긴 장수. 이만큼은 사진첩에 들어가 있다. */
  savedCount: number;
  onDiscard: () => void;
  onClose: () => void;
}

/**
 * 받아둔 묶음을 버리기 전에 한 번 묻는다.
 *
 * 버리면 다시 받아야 한다. 몇 장을 받아두는 데 꽤 걸렸을 수 있어서, 잘못 누른 X 로
 * 그 시간을 날리지 않게 한다. 이미 넘긴 묶음은 사진첩에 남는다는 것도 함께 말해준다.
 */
export const DiscardShareModal = ({
  remainingCount,
  savedCount,
  onDiscard,
  onClose,
}: DiscardShareModalProps) => {
  return (
    <Modal onClose={onClose} showClose={false}>
      <Content>
        <Title>남은 {remainingCount}장은 저장하지 않을까요?</Title>
        <Description>
          {savedCount > 0
            ? `저장한 ${savedCount}장은 사진첩에 그대로 있어요`
            : "받아둔 사진은 다시 받아야 해요"}
        </Description>
        <Actions>
          <CloseAction variant="default" onClick={onDiscard}>
            그만두기
          </CloseAction>
          <KeepAction variant="primary" onClick={onClose}>
            계속 저장
          </KeepAction>
        </Actions>
      </Content>
    </Modal>
  );
};
