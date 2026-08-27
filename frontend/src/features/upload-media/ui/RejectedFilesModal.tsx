import { LuFileWarning, LuImage, LuTriangleAlert, LuVideo } from "react-icons/lu";

import { Modal } from "@/shared/ui/modal";
import { formatBytes } from "../lib/formatBytes";
import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES, mediaKindOf } from "../lib/mediaFileRules";
import type { RejectedSelection } from "../model/selectMediaFiles";
import {
  ConfirmAction,
  Content,
  FileList,
  FileName,
  FileRow,
  FileTexts,
  Head,
  LimitChip,
  Limits,
  Reason,
  Thumb,
  Title,
  WarnBadge,
} from "./RejectedFilesModal.styles";

export interface RejectedFilesModalProps {
  /** 못 올리는 파일들. 비어 있으면 부르는 쪽이 아예 렌더링하지 않는다. */
  rejected: RejectedSelection[];
  onClose: () => void;
}

/**
 * 고른 파일 중 **못 올리는 것**을 알린다 (시안 07d).
 *
 * 파일마다 이름·크기·사유를 하나씩 보여준다. 사유별로 접어서 "3장은 올릴 수 없어요" 라고만
 * 하면, 사용자는 **어느 사진이 빠졌는지** 알 수 없어 다시 고를 때 같은 실수를 반복한다.
 *
 * 재시도가 없는 이유는 여기 오는 파일이 전부 **다시 눌러도 똑같이 거절될 것**이라서다.
 * 10MB 넘는 사진은 몇 번을 골라도 10MB 다. 사용자가 할 일은 확인하고 다른 파일을 고르는 것뿐이다.
 * 시도했다가 실패한 것(`UploadFailureModal`)과는 성격이 완전히 다르다.
 */

/** 한도는 파일마다 반복하지 않고 아래에서 한 번만 말한다. */
const LIMITS = [
  { icon: LuImage, label: `이미지 ~${formatBytes(MAX_IMAGE_BYTES)}` },
  { icon: LuVideo, label: `영상 ~${formatBytes(MAX_VIDEO_BYTES)}` },
];

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
    <Modal onClose={onClose}>
      <Content>
        <Head>
          <WarnBadge>
            <LuTriangleAlert aria-hidden />
          </WarnBadge>
          <Title>{titleOf(rejected)}</Title>
        </Head>

        <FileList>
          {rejected.map(({ fileName, size, message }) => (
            <FileRow key={fileName}>
              <Thumb>
                <ThumbIcon fileName={fileName} />
              </Thumb>
              <FileTexts>
                <FileName title={fileName}>
                  {fileName} · {formatBytes(size)}
                </FileName>
                <Reason>{message}</Reason>
              </FileTexts>
            </FileRow>
          ))}
        </FileList>

        <Limits>
          {LIMITS.map(({ icon: Icon, label }) => (
            <LimitChip key={label}>
              <Icon aria-hidden />
              {label}
            </LimitChip>
          ))}
        </Limits>

        <ConfirmAction variant="primary" size="lg" onClick={onClose}>
          확인
        </ConfirmAction>
      </Content>
    </Modal>
  );
};

/**
 * 원본을 읽어 썸네일을 만들지 않는다. 여기 오는 파일은 한도를 넘긴 것들이라
 * 2GB 짜리 영상이 섞여 있고, 미리보기를 만들자고 그걸 통째로 읽을 이유가 없다.
 * 확장자로 사진인지 영상인지만 가른다.
 */
const ThumbIcon = ({ fileName }: { fileName: string }) => {
  const kind = mediaKindOf(fileName);

  if (kind === "IMAGE") return <LuImage aria-hidden />;
  if (kind === "VIDEO") return <LuVideo aria-hidden />;

  return <LuFileWarning aria-hidden />;
};
