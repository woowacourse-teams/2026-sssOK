import type { Meta, StoryObj } from "@storybook/react-webpack5";

import type { RejectedSelection } from "../model/selectMediaFiles";
import { SelectionNotice } from "./SelectionNotice";

const rejection = (fileName: string, message: string): RejectedSelection => ({
  fileName,
  code:
    message.includes("MB") || message.includes("GB")
      ? "FILE_SIZE_EXCEEDED"
      : "UNSUPPORTED_FILE_TYPE",
  message,
});

const meta = {
  title: "features/upload-media/SelectionNotice",
  component: SelectionNotice,
  args: { onDismiss: () => {} },
} satisfies Meta<typeof SelectionNotice>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * 걸러진 것이 없으면 이 알림은 뜨지 않는다. 그래서 "전부 통과" 스토리가 없다 —
 * 고른 파일이 전부 올릴 수 있는 것이면 화면에 아무것도 추가되지 않는다.
 */
export const OneReason: Story = {
  args: {
    selection: {
      accepted: [],
      rejected: [rejection("clip.avi", "이미지와 영상만 올릴 수 있어요")],
    },
  },
};

/** 사유가 섞였을 때 — 사유별로 몇 장인지 묶어서 보여준다 */
export const MixedReasons: Story = {
  args: {
    selection: {
      accepted: [],
      rejected: [
        rejection("IMG_0001.HEIC", "이미지와 영상만 올릴 수 있어요"),
        rejection("IMG_0002.HEIC", "이미지와 영상만 올릴 수 있어요"),
        rejection("big.png", "사진은 10MB까지 올릴 수 있어요"),
      ],
    },
  },
};
