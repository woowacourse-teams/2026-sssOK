import { useRef, type ChangeEvent } from "react";
import { LuPlus } from "react-icons/lu";

import { MEDIA_FILE_ACCEPT } from "../lib/mediaFileRules";
import { selectMediaFiles, type MediaSelection } from "../model/selectMediaFiles";
import { Dock, FloatingUploadButton, HiddenFileInput } from "./UploadButton.styles";

export const UPLOAD_BUTTON_LABEL = "사진 올리기";

interface UploadButtonProps {
  /** 선택/진행 바와 겹치지 않도록 버튼만 숨기고 파일 입력은 유지한다. */
  hidden?: boolean;
  /** 고른 파일을 검증해 넘긴다. 아무것도 고르지 않고 취소하면 불리지 않는다. */
  onSelect: (selection: MediaSelection) => void;
}

/**
 * 기기 기본 사진 선택기를 여는 진입점.
 */
export const UploadButton = ({ onSelect, hidden = false }: UploadButtonProps) => {
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
      {!hidden && (
        <Dock>
          <FloatingUploadButton
            type="button"
            aria-label={UPLOAD_BUTTON_LABEL}
            onClick={() => inputRef.current?.click()}
          >
            <LuPlus size={19} aria-hidden="true" focusable="false" />
            올리기
          </FloatingUploadButton>
        </Dock>
      )}
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
