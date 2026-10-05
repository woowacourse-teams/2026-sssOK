import { HiCheckCircle } from "react-icons/hi2";
import { LuCheck, LuDownload, LuFolderInput, LuTrash2 } from "react-icons/lu";

import { FloatingBar } from "@/shared/ui/floating-bar";
import { IconButton } from "@/shared/ui/icon-button";
import {
  ActionGroup,
  Count,
  DownloadButton,
  SelectionCheck,
  SelectionLayout,
  SelectionSummary,
  ToastClose,
  ToastFrame,
  ToastIcon,
  ToastText,
} from "./OnboardingPreviewBars.styles";

/*
 * 홈 미리보기에 그리는 실제 화면의 하단 바들. 생김새와 문구는 실제 컴포넌트와 같고,
 * 누를 수 없는 그림이라 버튼 대신 span 으로 그린다(미리보기 전체가 inert 다).
 */

interface SelectionProps {
  count: number;
  pressed: boolean;
}

/** features/download-media 의 SelectionDownloadBar 가 사진을 고른 뒤 보이는 모습 */
export const PreviewSelectionBar = ({ count, pressed }: SelectionProps) => (
  <FloatingBar>
    <SelectionLayout>
      <SelectionSummary>
        <SelectionCheck>
          <LuCheck />
        </SelectionCheck>
        <Count>{count}개</Count>
      </SelectionSummary>
      <DownloadButton $pressed={pressed}>
        <LuDownload />
        다운로드
      </DownloadButton>
      <ActionGroup>
        <IconButton size="sm" variant="danger" tabIndex={-1}>
          <LuTrash2 />
        </IconButton>
        <IconButton size="sm" tabIndex={-1}>
          <LuFolderInput />
        </IconButton>
      </ActionGroup>
    </SelectionLayout>
  </FloatingBar>
);

/** shared/ui/toast 의 Toast(성공) 와 같은 모습 */
export const PreviewToast = ({ message }: { message: string }) => (
  <ToastFrame>
    <ToastIcon>
      <HiCheckCircle />
    </ToastIcon>
    <ToastText>{message}</ToastText>
    <ToastClose>닫기</ToastClose>
  </ToastFrame>
);
