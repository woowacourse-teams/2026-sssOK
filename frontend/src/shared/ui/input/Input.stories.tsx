import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { useState } from "react";

import { Input } from "./Input";
import type { InputProps } from "./Input";

const InteractiveInput = (props: InputProps) => {
  const [value, setValue] = useState("");

  return (
    <Input
      {...props}
      value={value}
      readOnly={false}
      onChange={(event) => setValue(event.target.value.slice(0, props.maxLength))}
    />
  );
};

const meta = {
  title: "Shared/Input",
  component: Input,
  args: {
    label: "입력한 이름은 다른 사람에게 보여요",
    value: "",
    maxLength: 10,
    placeholder: "이름을 입력해주세요.",
    readOnly: true,
  },
} satisfies Meta<typeof Input>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: (args) => <InteractiveInput {...args} />,
};

export const WithValue: Story = {
  args: {
    value: "윤돌",
  },
};

export const Error: Story = {
  args: {
    errorMessage: "이름을 입력해주세요.",
  },
};
