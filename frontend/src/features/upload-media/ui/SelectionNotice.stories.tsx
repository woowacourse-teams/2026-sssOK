import type { Meta, StoryObj } from "@storybook/react-webpack5";

import type { RejectedSelection } from "../model/selectMediaFiles";
import { SelectionNotice } from "./SelectionNotice";

const fileOf = (name: string) => new File([""], name, { type: "image/jpeg" });

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

/** 고른 파일이 전부 올릴 수 있는 것일 때 */
export const AllAccepted: Story = {
  args: {
    selection: { accepted: [fileOf("a.jpg"), fileOf("b.png"), fileOf("c.mov")], rejected: [] },
  },
};

/** 일부만 걸러졌을 때 — 사유별로 몇 장인지 함께 보여준다 */
export const PartiallyRejected: Story = {
  args: {
    selection: {
      accepted: [fileOf("a.jpg")],
      rejected: [
        rejection("IMG_0001.HEIC", "이미지와 영상만 올릴 수 있어요"),
        rejection("IMG_0002.HEIC", "이미지와 영상만 올릴 수 있어요"),
        rejection("big.png", "사진은 10MB까지 올릴 수 있어요"),
      ],
    },
  },
};

/** 고른 파일이 전부 걸러졌을 때 — 선택한 장수 줄이 사라진다 */
export const AllRejected: Story = {
  args: {
    selection: {
      accepted: [],
      rejected: [rejection("clip.avi", "이미지와 영상만 올릴 수 있어요")],
    },
  },
};
