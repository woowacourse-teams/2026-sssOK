import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { NameEntryBottomSheet } from "./NameEntryBottomSheet";

const meta = {
  title: "features/join-room/NameEntryBottomSheet",
  component: NameEntryBottomSheet,
} satisfies Meta<typeof NameEntryBottomSheet>;

export default meta;
type Story = StoryObj<typeof meta>;

/** 02-1 · 이름 입력 모달 — 이름을 입력하기 전 */
export const Empty: Story = {
  args: {
    onSubmit: () => {},
  },
};

/** 02-1 · 이름 입력 모달 후 — 인증 요청 중 */
export const Pending: Story = {
  args: {
    onSubmit: () => {},
    isPending: true,
  },
};
