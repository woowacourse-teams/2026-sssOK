import { useRef, type ChangeEvent } from "react";
import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { MEDIA_FILE_ACCEPT } from "../lib/mediaFileRules";
import { selectMediaFiles, type MediaSelection } from "../model/selectMediaFiles";

export const UPLOAD_BUTTON_LABEL = "사진 올리기";

interface UploadButtonProps {
  /** 고른 파일을 검증해 넘긴다. 아무것도 고르지 않고 취소하면 불리지 않는다. */
  onSelect: (selection: MediaSelection) => void;
}

/**
 * 기기 기본 사진 선택기를 여는 진입점.
 *
 * 여기서 바꿀 게 있다면 `<button>` 을 그것으로 갈아끼우는 것뿐이고, 아래 두 가지는 그대로 둔다.
 */
export const UploadButton = ({ onSelect }: UploadButtonProps) => {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.target;
    const files = Array.from(input.files ?? []);

    // 매번 비워야 같은 사진을 연속으로 두 번 골랐을 때도 change 가 다시 온다.
    input.value = "";

    if (files.length === 0) return;

    onSelect(selectMediaFiles(files));
  };

  return (
    <>
      <InterimButton type="button" onClick={() => inputRef.current?.click()}>
        {UPLOAD_BUTTON_LABEL}
      </InterimButton>
      {/*
        누름을 받는 건 위 버튼이라 선택기는 접근성 트리에서 뺀다.
        `display: none` 이 아니라 잘라서 숨기는 이유는 사파리가 숨긴 입력의 click() 을
        무시하는 경우가 있어서다.
      */}
      <HiddenFileInput
        ref={inputRef}
        type="file"
        accept={MEDIA_FILE_ACCEPT}
        multiple
        tabIndex={-1}
        aria-hidden
        onChange={handleChange}
      />
    </>
  );
};

/** 페어 버튼이 오면 통째로 지운다. */
const InterimButton = styled.button`
  padding: ${spacing[8]} ${spacing[16]};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[12]};
  color: ${colors.textStrong};

  ${typography.label5}
`;

const HiddenFileInput = styled.input`
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  border: 0;
  margin: -1px;
  clip-path: inset(50%);
  overflow: hidden;
  white-space: nowrap;
`;
