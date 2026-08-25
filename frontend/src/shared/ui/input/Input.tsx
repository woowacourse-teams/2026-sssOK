import { useId, type InputHTMLAttributes } from "react";

import { Row } from "../row";
import { Stack } from "../stack";
import { Count, ErrorMessage, Label, StyledInput } from "./Input.styles";

export interface InputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> {
  label: string;
  errorMessage?: string;
  value: string;
  maxLength: number;
  onValueChange: (value: string) => void;
}

export const Input = ({
  label,
  errorMessage = "",
  value,
  maxLength,
  onValueChange,
  id,
  ...props
}: InputProps) => {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  return (
    <Stack gap={8}>
      <Label htmlFor={inputId}>{label}</Label>

      <StyledInput
        {...props}
        id={inputId}
        value={value}
        maxLength={maxLength}
        onChange={(event) => onValueChange(event.target.value)}
        aria-invalid={Boolean(errorMessage)}
        $hasError={Boolean(errorMessage)}
      />

      <Row justify="space-between">
        <ErrorMessage>{errorMessage}</ErrorMessage>
        <Count>
          {value.length}/{maxLength}
        </Count>
      </Row>
    </Stack>
  );
};
