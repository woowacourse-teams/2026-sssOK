import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { useState } from "react";

import { Slider } from "./Slider";
import type { SliderProps } from "./Slider";

const InteractiveSlider = (props: SliderProps) => {
  const [value, setValue] = useState(props.value);

  return <Slider {...props} value={value} onValueChange={setValue} />;
};

const meta = {
  title: "Shared/Slider",
  component: Slider,
  args: {
    label: "방 만료 기간",
    name: "expiryDays",
    value: 1,
    min: 1,
    max: 14,
    formatValue: (value: number) => `${value}일`,
    marks: [
      { value: 1, label: "1일" },
      { value: 7, label: "1주" },
      { value: 14, label: "2주" },
    ],
    onValueChange: () => undefined,
  },
  render: (args) => <InteractiveSlider {...args} />,
} satisfies Meta<typeof Slider>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Disabled: Story = {
  args: { disabled: true },
};
