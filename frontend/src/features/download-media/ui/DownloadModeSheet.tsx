import { useId, useState } from "react";
import { HiCheck } from "react-icons/hi2";
import { LuDownload, LuFileArchive, LuImageDown } from "react-icons/lu";

import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { prefersShareSheet } from "../lib/prefersShareSheet";
import { zipArchiveName } from "../lib/zipArchiveName";
import type { DownloadMode } from "../model/types";
import {
  Check,
  Description,
  IconSlot,
  Label,
  Notice,
  Option,
  Options,
  Submit,
  Texts,
} from "./DownloadModeSheet.styles";

/**
 * 어떻게 받을지 고르는 시트.
 *
 * **고르는 것과 시작하는 것이 나뉘어 있다.** 항목을 누르면 선택만 바뀌고, 실제 다운로드는
 * 아래 버튼을 눌러야 시작한다 — 잘못 누르면 되돌릴 수 없는 동작이라 한 번 더 확인을 받는다.
 */

interface ModeOption {
  value: DownloadMode;
  icon: typeof LuDownload;
  label: string;
  description: string;
}

interface DownloadModeSheetProps {
  /** 고른 장수. 제목에 그대로 들어간다. */
  count: number;
  /** zip 파일명을 미리 보여주는 데 쓴다. */
  roomCode: string;
  onSubmit: (mode: DownloadMode) => void;
  onClose: () => void;
}

export const DownloadModeSheet = ({
  count,
  roomCode,
  onSubmit,
  onClose,
}: DownloadModeSheetProps) => {
  // zip 이 기본이다. 여러 장을 받을 때 실제로 끝까지 동작하는 쪽이라서다.
  const [mode, setMode] = useState<DownloadMode>("zip");
  const name = useId();

  /**
   * 첫 항목은 **기기에 따라 다른 것이 앉는다.**
   *
   * 둘 다 "한 장씩" 받는다는 점은 같고, 그 낱장이 어디에 놓이느냐만 다르다 —
   * 데스크톱은 다운로드 폴더, 폰은 사진첩이다. 폰에서 낱장 저장은 첫 장만 받아지므로
   * 아예 항목을 내주지 않는다 (`prefersShareSheet` 참고).
   */
  const oneByOne: ModeOption = prefersShareSheet()
    ? {
        value: "share",
        icon: LuImageDown,
        label: "사진첩에 저장",
        description: "한 장씩 사진 앱으로",
      }
    : {
        value: "individual",
        icon: LuDownload,
        label: "개별로 저장",
        description: "한 장씩 원본 그대로",
      };

  const options: ModeOption[] = [
    oneByOne,
    {
      value: "zip",
      icon: LuFileArchive,
      label: ".zip 일괄 다운로드",
      description: zipArchiveName(roomCode),
    },
  ];

  return (
    <BottomSheet title={`${count}개를 어떻게 받을까요?`} onClose={onClose}>
      <Options role="radiogroup">
        {options.map(({ value, icon: Icon, label, description }) => (
          <Option key={value} $selected={mode === value}>
            <input
              type="radio"
              name={name}
              value={value}
              checked={mode === value}
              onChange={() => setMode(value)}
            />
            <IconSlot>
              <Icon />
            </IconSlot>
            <Texts>
              <Label>{label}</Label>
              <Description>{description}</Description>
            </Texts>
            {mode === value && (
              <Check aria-hidden>
                <HiCheck />
              </Check>
            )}
          </Option>
        ))}
      </Options>

      {/*
        공유 시트에서 사진첩에 넣는 것은 **아이콘 줄이 아니라 아래 액션 목록의 "이미지 저장"** 이다.
        아이콘 줄의 사진 관련 앱은 그 앱으로 보내는 것이라, 눌러도 카메라롤에는 남지 않는다.
        버튼에 "사진첩에 저장" 이라고만 써 두면 시트에서 한 번 더 골라야 하는 줄 모르고
        눈에 먼저 띄는 아이콘을 누르게 된다 — 그래서 어디를 눌러야 하는지 미리 말해준다.
      */}
      <Notice>
        {mode === "share"
          ? "공유 시트가 열리면 [이미지 저장]을 눌러야 사진첩에 들어가요"
          : "업로드 당시 원본 파일명을 그대로 유지해요"}
      </Notice>

      <Submit type="button" onClick={() => onSubmit(mode)}>
        다운로드
      </Submit>
    </BottomSheet>
  );
};
