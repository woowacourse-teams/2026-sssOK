import { useId, type ChangeEvent } from "react";

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
  onChange: (value: string) => void;
  disabled?: boolean;
}

export const RadioGroup = ({
  label,
  name,
  value,
  options,
  onChange,
  disabled = false,
}: RadioGroupProps) => {
  const labelId = useId();

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChange(event.target.value);
  };

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
              onChange={handleChange}
              disabled={disabled}
            />
            <Option>{option.label}</Option>
          </OptionLabel>
        ))}
      </RadioGroupContainer>
    </Stack>
  );
};
