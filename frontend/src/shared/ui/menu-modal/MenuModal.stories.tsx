import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MenuModal } from "./MenuModal";
import { Divider } from "../divider/Divider";

const meta = {
  title: "shared/ui/MenuModal",
  component: MenuModal,
} satisfies Meta<typeof MenuModal>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RoomFolderMenu: Story = {
  args: {
    onClose: () => {},
    children: (
      <>
        <button type="button">방 설정</button>
        <button type="button">방 삭제</button>
        <Divider />
        <button type="button">폴더 추가</button>
        <button type="button">폴더 설정</button>
        <button type="button">폴더 삭제</button>
      </>
    ),
  },
};
