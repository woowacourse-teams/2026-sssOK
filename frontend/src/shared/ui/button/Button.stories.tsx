import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { Button } from "./Button";

const meta = {
  title: "Shared/Button",
  component: Button,
  args: {
    children: "버튼",
    variant: "primary",
    size: "sm",
  },
} satisfies Meta<typeof Button>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Primary: Story = {};

export const Default: Story = {
  args: {
    variant: "default",
  },
};

export const Danger: Story = {
  args: {
    children: "삭제",
    variant: "danger",
  },
};

export const Large: Story = {
  args: {
    size: "lg",
  },
};

export const Disabled: Story = {
  args: {
    disabled: true,
  },
};
