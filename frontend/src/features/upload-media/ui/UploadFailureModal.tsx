import { Modal } from "@/shared/ui/modal";
import { uploadFailureReasonOf } from "../lib/uploadFailureReason";
import type { FailedUpload } from "../model/types";
import {
  Actions,
  CloseAction,
  Content,
  Description,
  PrimaryAction,
  Title,
} from "./FailureDialog.styles";
import { FileReasonList } from "./FileReasonList";

export interface UploadFailureModalProps {
  /**
   * 다시 올릴 수 있는 실패분. **재시도가 실제로 다시 올릴 목록과 같아야 한다** —
   * `retryableFailuresOf` 가 추린 그대로를 넘긴다.
   */
  failures: FailedUpload[];
  /**
   * 다시 올릴 길. **없으면 재시도 버튼을 내주지 않는다** — 올릴 권한을 잃은 뒤라
   * 눌러봐야 서버가 막는다. 눌리는데 아무 일도 없는 버튼이 제일 나쁘다.
   */
  onRetry?: () => void;
  onClose: () => void;
}

/**
 * 업로드가 끝났을 때 깨진 파일이 있으면 뜨는 모달 (시안 07g).
 *
 * **어느 파일이 왜 깨졌는지까지 말한다.** 장수만 알려주면 사용자는 재시도를 누를지
 * 말지 판단할 근거가 없다 — 회선 문제면 다시 누르면 되고, 특정 파일만 계속 걸리면
 * 그 파일을 빼야 한다.
 *
 * 여기 오는 건 전부 **다시 올리면 될 수도 있는** 실패다. 파일 자체가 조건에 안 맞는 것
 * (`RejectedFilesModal`)과 사용자가 직접 누른 취소는 애초에 이 모달에 오지 않는다.
 */
export const UploadFailureModal = ({ failures, onRetry, onClose }: UploadFailureModalProps) => {
  return (
    // 카드 안에 닫기가 이미 있다. X 를 또 두면 같은 일을 하는 버튼이 둘이 된다.
    <Modal onClose={onClose} showClose={false}>
      <Content>
        <Title>앗, {failures.length}장을 못 올렸어요</Title>
        <Description>
          네트워크가 끊겼거나 예기치 못한 이유로 실패했어요.
          {onRetry && (
            <>
              <br />
              실패한 파일만 다시 시도해보세요!
            </>
          )}
        </Description>

        <FileReasonList
          items={failures.map(({ fileName, code }) => ({
            fileName,
            reason: uploadFailureReasonOf(code),
          }))}
        />

        <Actions>
          {/* 거절 모달과 같은 크기를 쓴다. 같은 골격인데 버튼 높이만 다르면 다른 화면처럼 보인다. */}
          <CloseAction variant="default" size="sm" onClick={onClose}>
            닫기
          </CloseAction>
          {onRetry && (
            <PrimaryAction variant="primary" size="sm" onClick={onRetry}>
              실패만 재시도
            </PrimaryAction>
          )}
        </Actions>
      </Content>
    </Modal>
  );
};
