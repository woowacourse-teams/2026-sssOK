import { useState } from "react";

import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import { Textarea } from "./Textarea";

const meta = {
  title: "Shared/Textarea",
  component: Textarea,
  render: function Render(args) {
    const [value, setValue] = useState(args.value);

    return (
      <Textarea
        {...args}
        value={value}
        onValueChange={(value) => {
          setValue(value);
          args.onValueChange(value);
        }}
      />
    );
  },

  args: {
    label: "문의 내용",
    placeholder: "예) 업로드 속도가 너무 느려요",
    value: "",
    maxLength: 300,
    onValueChange: fn(),
  },
} satisfies Meta<typeof Textarea>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default = {} satisfies Story;

export const Error = {
  args: {
    errorMessage: "문의 내용을 입력해주세요",
  },
} satisfies Story;

export const WithValue = {
  args: {
    value: "업로드 속도가 너무 느려요.",
  },
} satisfies Story;
