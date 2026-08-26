import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { IconButton } from "./IconButton";

const CloseIcon = () => (
  <svg
    aria-hidden="true"
    width="20"
    height="20"
    viewBox="0 0 20 20"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M5 5L15 15M15 5L5 15"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
    />
  </svg>
);

const meta = {
  title: "Shared/IconButton",
  component: IconButton,
  args: {
    "aria-label": "닫기",
    children: <CloseIcon />,
    size: "md",
    variant: "default",
  },
} satisfies Meta<typeof IconButton>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Small: Story = {
  args: {
    size: "sm",
  },
};

export const Danger: Story = {
  args: {
    "aria-label": "삭제",
    variant: "danger",
  },
};
