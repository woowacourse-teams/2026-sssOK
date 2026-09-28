import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { colors } from "@/shared/styles/tokens";
import { Spinner } from "./Spinner";

const meta = {
  title: "Shared/Spinner",
  component: Spinner,
  args: {
    label: "불러오는 중이에요.",
    size: "md",
    tone: "default",
    delayMs: 0,
  },
} satisfies Meta<typeof Spinner>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Large: Story = {
  args: {
    size: "lg",
  },
};

export const Inverse: Story = {
  args: {
    tone: "inverse",
  },
  decorators: [
    (Story) => (
      <div style={{ padding: 24, backgroundColor: colors.backgroundInverse }}>
        <Story />
      </div>
    ),
  ],
};
