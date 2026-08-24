import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { Badge } from "./Badge";

const meta = {
  title: "Shared/Badge",
  component: Badge,
  args: {
    children: "배지",
    size: "sm",
    variant: "neutral",
  },
} satisfies Meta<typeof Badge>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Neutral: Story = {};

export const Primary: Story = {
  args: {
    children: "나",
    variant: "primary",
  },
};

export const Soft: Story = {
  args: {
    size: "md",
    children: "윤돌",
    variant: "soft",
  },
};
