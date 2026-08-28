import { Modal } from "@/shared/ui/modal";
import { formatBytes } from "../lib/formatBytes";
import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES } from "../lib/mediaFileRules";
import type { RejectedSelection } from "../model/selectMediaFiles";
import { Actions, Content, Description, PrimaryAction, Title } from "./FailureDialog.styles";
import { FileReasonList } from "./FileReasonList";

export interface RejectedFilesModalProps {
  /** 못 올리는 파일들. 비어 있으면 부르는 쪽이 아예 렌더링하지 않는다. */
  rejected: RejectedSelection[];
  onClose: () => void;
}

/**
 * 고른 파일 중 **못 올리는 것**을 알린다 (시안 07d).
 *
 * 실패 모달(`UploadFailureModal`)과 같은 골격을 쓴다 — 사용자가 보는 것은 둘 다
 * "안 된 파일 목록" 이라 생김새가 다를 이유가 없다. 다른 것은 **재시도가 없다는 점**뿐이다.
 * 10MB 넘는 사진은 몇 번을 골라도 10MB 라, 할 수 있는 일은 확인하고 다른 파일을 고르는 것뿐이다.
 */

/** 한도는 파일마다 반복하지 않고 부제에서 한 번만 말한다. */
const LIMIT_TEXT = `이미지 ${formatBytes(MAX_IMAGE_BYTES)} · 영상 ${formatBytes(MAX_VIDEO_BYTES)} 까지 올릴 수 있어요`;

/**
 * 제목은 걸러진 사유에 따라 달라진다.
 *
 * 시안에 있는 것은 용량 초과(`파일이 너무 커요`) 하나뿐이다. 형식 오류와 섞였을 때까지
 * `파일이 너무 커요` 라고 하면 절반은 거짓말이 되므로, 섞이면 두 경우를 다 덮는 말로 물러선다.
 */
const titleOf = (rejected: RejectedSelection[]) => {
  const codes = new Set(rejected.map((one) => one.code));

  if (codes.size === 1 && codes.has("FILE_SIZE_EXCEEDED")) {
    return "파일이 너무 커요";
  }

  if (codes.size === 1 && codes.has("UNSUPPORTED_FILE_TYPE")) {
    return "올릴 수 없는 형식이에요";
  }

  return `${rejected.length}장은 올릴 수 없어요`;
};

export const RejectedFilesModal = ({ rejected, onClose }: RejectedFilesModalProps) => {
  return (
    <Modal onClose={onClose} showClose={false}>
      <Content>
        <Title>{titleOf(rejected)}</Title>
        <Description>
          {LIMIT_TEXT}
          <br />
          나머지는 그대로 올라가고 있어요.
        </Description>

        {/* 크기를 파일명에 붙여 보여준다 — 한도를 얼마나 넘었는지가 곧 사유다. */}
        <FileReasonList
          items={rejected.map(({ fileName, size, code }) => ({
            fileName: `${fileName} · ${formatBytes(size)}`,
            reason: code === "FILE_SIZE_EXCEEDED" ? "용량 초과" : "지원 안 함",
          }))}
        />

        <Actions>
          {/* 시안의 버튼은 목록 한 줄보다 조금 높은 정도다 — `lg` 는 시안보다 한참 크다. */}
          <PrimaryAction variant="primary" size="sm" onClick={onClose}>
            확인
          </PrimaryAction>
        </Actions>
      </Content>
    </Modal>
  );
};
