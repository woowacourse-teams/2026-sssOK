import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { DropdownMenu, DropdownMenuItem } from "./DropdownMenu";
import { Divider } from "../divider/Divider";

const meta = {
  title: "shared/ui/DropdownMenu",
  component: DropdownMenu,
  decorators: [
    (Story) => (
      <div style={{ position: "relative", display: "inline-flex" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof DropdownMenu>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RoomFolderMenu: Story = {
  args: {
    onClose: () => {},
    children: (
      <>
        <DropdownMenuItem>방 설정</DropdownMenuItem>
        <DropdownMenuItem tone="danger">방 삭제</DropdownMenuItem>
        <Divider />
        <DropdownMenuItem>폴더 추가</DropdownMenuItem>
        <DropdownMenuItem>폴더 설정</DropdownMenuItem>
        <DropdownMenuItem tone="danger">폴더 삭제</DropdownMenuItem>
      </>
    ),
  },
};
