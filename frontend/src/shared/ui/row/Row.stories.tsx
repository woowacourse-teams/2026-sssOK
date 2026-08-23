// Row.stories.tsx
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { Row } from "./Row";

const meta = {
  title: "Shared/Row",
  component: Row,
  args: {
    gap: 8,
    align: "center",
    justify: "flex-start",
  },
} satisfies Meta<typeof Row>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: (args) => (
    <Row {...args}>
      <div>Item 1</div>
      <div>Item 2</div>
      <div>Item 3</div>
    </Row>
  ),
};

export const LargeGap: Story = {
  args: {
    gap: 24,
  },
  render: (args) => (
    <Row {...args}>
      <div>Item 1</div>
      <div>Item 2</div>
      <div>Item 3</div>
    </Row>
  ),
};

export const Center: Story = {
  args: {
    justify: "center",
  },
  render: (args) => (
    <Row {...args}>
      <div>Left</div>
      <div>Right</div>
    </Row>
  ),
};

export const Between: Story = {
  args: {
    justify: "space-between",
  },
  render: (args) => (
    <Row {...args}>
      <div>Left</div>
      <div>Right</div>
    </Row>
  ),
};
