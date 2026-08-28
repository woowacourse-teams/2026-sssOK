import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { RejectedFilesModal } from "./RejectedFilesModal";
import type { RejectedSelection } from "../model/selectMediaFiles";

const MB = 1024 * 1024;

const unsupported = (fileName: string, mb: number): RejectedSelection => ({
  fileName,
  size: mb * MB,
  code: "UNSUPPORTED_FILE_TYPE",
  message: "이미지와 영상만 올릴 수 있어요",
});

const oversized = (fileName: string, mb: number): RejectedSelection => ({
  fileName,
  size: mb * MB,
  code: "FILE_SIZE_EXCEEDED",
  message: "이미지 최대 10MB 초과",
});

const meta = {
  title: "features/upload-media/RejectedFilesModal",
  component: RejectedFilesModal,
  parameters: {
    // 오버레이가 화면을 덮는 모달이다. 캔버스를 꽉 채워야 실제 비율이 보인다.
    layout: "fullscreen",
  },
} satisfies Meta<typeof RejectedFilesModal>;

export default meta;
type Story = StoryObj<typeof meta>;

/** 07d · 형식과 용량이 섞여 걸린 경우 — 시안 그대로의 상태 */
export const Default: Story = {
  args: {
    rejected: [
      unsupported("IMG_0421.HEIC", 4),
      unsupported("IMG_0422.HEIC", 3),
      oversized("노을_2026.png", 14),
    ],
    onClose: () => {},
  },
};

/** 용량만 걸렸을 때 */
export const OnlyOversized: Story = {
  args: {
    rejected: [oversized("노을_2026.png", 14)],
    onClose: () => {},
  },
};

/** 목록이 길어 상자가 스크롤될 때 */
export const ManyFiles: Story = {
  args: {
    rejected: Array.from({ length: 12 }, (_, index) =>
      unsupported(`IMG_${400 + index}.HEIC`, 3 + index),
    ),
    onClose: () => {},
  },
};
