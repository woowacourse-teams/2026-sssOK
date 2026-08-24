import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { BottomSheet } from "./BottomSheet";

const meta = {
  title: "shared/ui/BottomSheet",
  component: BottomSheet,
} satisfies Meta<typeof BottomSheet>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NewFolder: Story = {
  args: {
    title: "새 폴더 만들기",
    onClose: () => {},
  },
};
