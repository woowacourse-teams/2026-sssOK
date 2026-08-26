import { useId } from "react";

import { Stack } from "../stack";
import { Option, OptionLabel, RadioGroupContainer, RadioGroupLabel } from "./RadioGroup.styles";

export interface RadioOption {
  label: string;
  value: string;
}

export interface RadioGroupProps {
  label: string;
  name: string;
  value: string;
  options: RadioOption[];
  onValueChange: (value: string) => void;
  disabled?: boolean;
}

export const RadioGroup = ({
  label,
  name,
  value,
  options,
  onValueChange,
  disabled = false,
}: RadioGroupProps) => {
  const labelId = useId();

  return (
    <Stack gap={8}>
      <RadioGroupLabel id={labelId}>{label}</RadioGroupLabel>

      <RadioGroupContainer
        role="radiogroup"
        aria-labelledby={labelId}
        $columnCount={options.length}
      >
        {options.map((option) => (
          <OptionLabel key={option.value}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onValueChange(option.value)}
              disabled={disabled}
            />
            <Option>{option.label}</Option>
          </OptionLabel>
        ))}
      </RadioGroupContainer>
    </Stack>
  );
};
