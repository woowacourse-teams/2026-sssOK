import { useId, type InputHTMLAttributes } from "react";

import { Row } from "../row";
import { Stack } from "../stack";
import { Count, ErrorMessage, Label, StyledInput } from "./Input.styles";

export interface InputProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
> {
  label: string;
  errorMessage?: string;
  value: string;
  /**
   * 글자 수 제한. 주면 입력을 그 길이로 자르고 `N/M` 카운터를 함께 보여준다.
   *
   * 아이디·비밀번호처럼 길이를 셀 이유가 없는 입력도 있어서 선택값이다 — 카운터를 늘 그리면
   * 세어 봐야 의미 없는 숫자가 붙는다.
   */
  maxLength?: number;
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
  const hasError = Boolean(errorMessage);

  return (
    <Stack gap={8}>
      <Label htmlFor={inputId}>{label}</Label>

      <StyledInput
        {...props}
        id={inputId}
        value={value}
        maxLength={maxLength}
        // maxLength 가 없으면 slice 의 끝이 undefined 라 원본이 그대로 간다.
        onChange={(event) => onValueChange(event.target.value.slice(0, maxLength))}
        aria-invalid={hasError}
        $hasError={hasError}
      />

      {/* 보여줄 게 하나도 없으면 줄 자체를 그리지 않는다 — 빈 줄만큼 아래가 벌어진다. */}
      {(hasError || maxLength !== undefined) && (
        <Row justify="space-between">
          <ErrorMessage>{errorMessage}</ErrorMessage>
          {maxLength !== undefined && (
            <Count>
              {value.length}/{maxLength}
            </Count>
          )}
        </Row>
      )}
    </Stack>
  );
};
