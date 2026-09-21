import { useId, type TextareaHTMLAttributes } from "react";

import { Row } from "../row";
import { Stack } from "../stack";
import { Count, ErrorMessage, Label, StyledTextarea } from "./Textarea.styles";

export interface TextareaProps extends Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  "value" | "onChange"
> {
  label: string;
  errorMessage?: string;
  value: string;
  maxLength: number;
  onValueChange: (value: string) => void;
}

export const Textarea = ({
  label,
  errorMessage = "",
  value,
  maxLength,
  onValueChange,
  id,
  ...props
}: TextareaProps) => {
  const generatedId = useId();
  const textareaId = id ?? generatedId;

  return (
    <Stack gap={8}>
      <Label htmlFor={textareaId}>{label}</Label>

      <StyledTextarea
        {...props}
        id={textareaId}
        value={value}
        maxLength={maxLength}
        onChange={(event) => onValueChange(event.target.value.slice(0, maxLength))}
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
