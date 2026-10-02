import { useId } from "react";

import { Stack } from "../stack";
import { Label, Mark, Marks, StyledSlider, Track, Value, ValueRow } from "./Slider.styles";

export interface SliderMark {
  value: number;
  label: string;
}

export interface SliderProps {
  label: string;
  name: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  formatValue?: (value: number) => string;
  marks?: SliderMark[];
  onValueChange: (value: number) => void;
  disabled?: boolean;
}

const toPercent = (value: number, min: number, max: number) =>
  max === min ? 0 : ((value - min) / (max - min)) * 100;

export const Slider = ({
  label,
  name,
  value,
  min,
  max,
  step = 1,
  formatValue = String,
  marks = [],
  onValueChange,
  disabled = false,
}: SliderProps) => {
  const inputId = useId();
  const valueText = formatValue(value);
  const progress = toPercent(value, min, max);

  return (
    <Stack gap={8}>
      <Label htmlFor={inputId}>{label}</Label>

      <Track>
        {/* 끄는 동안 시선이 동그라미에 있으므로 고른 값도 동그라미를 따라다니게 둔다. */}
        <ValueRow aria-hidden>
          <Value $percent={progress}>{valueText}</Value>
        </ValueRow>

        <StyledSlider
          id={inputId}
          type="range"
          name={name}
          value={value}
          min={min}
          max={max}
          step={step}
          aria-valuetext={valueText}
          disabled={disabled}
          $progress={progress}
          onChange={(event) => onValueChange(Number(event.target.value))}
        />

        {marks.length > 0 && (
          // 슬라이더 자체가 키보드·스크린리더로 조작되므로 눈금은 보조 터치 영역으로만 둔다.
          <Marks aria-hidden>
            {marks.map((mark) => (
              <Mark
                key={mark.value}
                $percent={toPercent(mark.value, min, max)}
                $disabled={disabled}
                onClick={() => !disabled && onValueChange(mark.value)}
              >
                {mark.label}
              </Mark>
            ))}
          </Marks>
        )}
      </Track>
    </Stack>
  );
};
