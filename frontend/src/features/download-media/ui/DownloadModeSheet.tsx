import { useId, useState } from "react";
import { HiCheck } from "react-icons/hi2";
import { LuDownload, LuFileArchive, LuImageDown } from "react-icons/lu";

import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { SHARE_BATCH_SIZE } from "../config";
import { isNativeApp, savesToPhotoLibrary } from "../lib/nativeApp";
import { zipArchiveName } from "../lib/zipArchiveName";
import { shareBatchCountOf } from "../model/shareBatches";
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
  const inNativeApp = isNativeApp();
  // 앱은 바이트를 웹뷰로 넘기지 않아 나눠 저장하지 않는다 (`nativeApp` 참고).
  const shareBatchCount = inNativeApp ? 1 : shareBatchCountOf(count);

  /**
   * 첫 항목은 **기기에 따라 다른 것이 앉는다.**
   *
   * 둘 다 "한 장씩" 받는다는 점은 같고, 그 낱장이 어디에 놓이느냐만 다르다 —
   * 데스크톱은 다운로드 폴더, 폰은 사진첩이다. 폰에서 낱장 저장은 첫 장만 받아지므로
   * 아예 항목을 내주지 않는다 (`prefersShareSheet` 참고). 앱에서는 기기와 상관없이 사진첩이다.
   */
  const oneByOne: ModeOption = savesToPhotoLibrary()
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
        많이 고르면 묶음으로 나눠 넘긴다. 두 번째 시트가 떴을 때 당황하지 않게,
        몇 번 눌러야 하는지 누르기 **전에** 말해준다.
      */}
      {mode === "share" && shareBatchCount > 1 && (
        <Notice>
          사진이 많아서 최대 {SHARE_BATCH_SIZE}장씩 {shareBatchCount}번 나눠 저장해요
        </Notice>
      )}

      <Submit type="button" onClick={() => onSubmit(mode)}>
        다운로드
      </Submit>
    </BottomSheet>
  );
};
