import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { NameEntryForm } from "./NameEntryForm";

const meta = {
  title: "features/join-room/NameEntryForm",
  component: NameEntryForm,
} satisfies Meta<typeof NameEntryForm>;

export default meta;
type Story = StoryObj<typeof meta>;

/** 02-1 · 이름 입력 — 이름을 입력하기 전 */
export const Empty: Story = {
  args: {
    onSubmit: () => {},
  },
};

/** 02-1 · 이름 입력 — 인증 요청 중 */
export const Pending: Story = {
  args: {
    onSubmit: () => {},
    isPending: true,
  },
};
