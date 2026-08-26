import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { useState } from "react";

import { RadioGroup } from "./RadioGroup";
import type { RadioGroupProps } from "./RadioGroup";

const InteractiveRadioGroup = (props: RadioGroupProps) => {
  const [value, setValue] = useState(props.value);

  return <RadioGroup {...props} value={value} onValueChange={setValue} />;
};

const meta = {
  title: "Shared/RadioGroup",
  component: RadioGroup,
  args: {
    label: "업로드 권한",
    name: "uploadPermission",
    value: "everyone",
    options: [
      { label: "누구나", value: "everyone" },
      { label: "방장만", value: "host" },
    ],
    onValueChange: () => undefined,
  },
  render: (args) => <InteractiveRadioGroup {...args} />,
} satisfies Meta<typeof RadioGroup>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const ThreeOptions: Story = {
  args: {
    options: [
      { label: "1일", value: "1day" },
      { label: "3일", value: "3day" },
      { label: "7일", value: "7day" },
    ],
    value: "1day",
  },
};
