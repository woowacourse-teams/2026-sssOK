import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { Divider } from "./Divider";

const meta = {
  title: "shared/ui/Divider",
  component: Divider,
} satisfies Meta<typeof Divider>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <div style={{ width: "200px" }}>
      <p>위 항목</p>
      <Divider />
      <p>아래 항목</p>
    </div>
  ),
};
