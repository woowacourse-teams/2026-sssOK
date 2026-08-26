import { LuLoaderCircle } from "react-icons/lu";

import { FloatingBar } from "@/shared/ui/floating-bar";
import {
  CancelButton,
  Count,
  Dock,
  Fill,
  Spinner,
  Status,
  StatusText,
} from "./UploadProgressBar.styles";

export interface UploadProgressBarProps {
  /** PUT 이 끝난 장수 */
  completedCount: number;
  /** 올리는 중인 전체 장수. 거절된 파일은 빠져 있다 */
  totalCount: number;
  /** 0~100. 장수가 아니라 바이트 기준이다 */
  percent: number;
  onCancel: () => void;
}

/**
 * 업로드가 도는 동안 화면 하단에 떠 있는 바 (#73).
 *
 * 상태를 스스로 만들지 않는다 — 띄울지 말지, 몇 퍼센트인지는 전부 부르는 쪽이 정한다.
 * 업로드가 끝나면 부르는 쪽이 이 컴포넌트를 렌더링하지 않는 것으로 바가 사라진다.
 */
export const UploadProgressBar = ({
  completedCount,
  totalCount,
  percent,
  onCancel,
}: UploadProgressBarProps) => {
  return (
    <Dock>
      <FloatingBar>
        <Count>
          {completedCount} / {totalCount}
        </Count>
        <Status
          variant="soft"
          role="progressbar"
          aria-label="업로드 진행률"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
        >
          <Fill style={{ width: `${percent}%` }} />
          <StatusText>
            <Spinner>
              <LuLoaderCircle />
            </Spinner>
            업로드 중... {percent}%
          </StatusText>
        </Status>
        <CancelButton type="button" onClick={onCancel}>
          취소
        </CancelButton>
      </FloatingBar>
    </Dock>
  );
};
