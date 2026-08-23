import { useId, type InputHTMLAttributes } from "react";

import { Row } from "../row";
import { Stack } from "../stack";
import { Count, ErrorMessage, Label, StyledInput } from "./Input.styles";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  errorMessage?: string;
  maxLength: number;
}

export const Input = ({
  label,
  errorMessage = "",
  value,
  maxLength,
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
        aria-invalid={Boolean(errorMessage)}
        $hasError={Boolean(errorMessage)}
      />

      <Row justify="space-between">
        <ErrorMessage>{errorMessage}</ErrorMessage>
        <Count>
          {typeof value === "string" ? value.length : 0}/{maxLength}
        </Count>
      </Row>
    </Stack>
  );
};
