import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { Stack } from "./Stack";

const meta = {
  title: "Shared/Stack",
  component: Stack,
  args: {
    gap: 8,
    align: "stretch",
    justify: "flex-start",
  },
} satisfies Meta<typeof Stack>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: (args) => (
    <Stack {...args}>
      <div>Item 1</div>
      <div>Item 2</div>
      <div>Item 3</div>
    </Stack>
  ),
};

export const LargeGap: Story = {
  args: {
    gap: 24,
  },
  render: (args) => (
    <Stack {...args}>
      <div>Item 1</div>
      <div>Item 2</div>
      <div>Item 3</div>
    </Stack>
  ),
};

export const Center: Story = {
  args: {
    justify: "center",
  },
  render: (args) => (
    <Stack {...args} style={{ minHeight: "160px" }}>
      <div>Top</div>
      <div>Bottom</div>
    </Stack>
  ),
};

export const Between: Story = {
  args: {
    justify: "space-between",
  },
  render: (args) => (
    <Stack {...args} style={{ minHeight: "160px" }}>
      <div>Top</div>
      <div>Bottom</div>
    </Stack>
  ),
};
