import { useState } from "react";
import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";
import type { MediaSelection } from "../model/selectMediaFiles";
import { SelectionNotice } from "./SelectionNotice";
import { UploadButton } from "./UploadButton";

/**
 * 업로드 진입점. 사진 선택기를 열고, 고른 파일을 검증해 결과를 알리는 데까지가 여기 몫이다.
 *
 * 전송은 아직 붙어 있지 않다 —
 * [#75](https://github.com/woowacourse-teams/2026-sssOK/issues/75) 가 `selection.accepted` 를
 * 받아 발급·PUT·등록으로 이어간다.
 */
export const MediaUploader = () => {
  const [selection, setSelection] = useState<MediaSelection | null>(null);

  return (
    <Stack>
      <UploadButton onSelect={setSelection} />
      <LiveRegion role="status" aria-live="polite">
        {selection !== null && (
          <SelectionNotice selection={selection} onDismiss={() => setSelection(null)} />
        )}
      </LiveRegion>
    </Stack>
  );
};

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 0 ${spacing[16]};
`;

/**
 * 알림이 뜨기 **전부터** DOM 에 있어야 스크린리더가 읽어준다.
 * 영역과 내용이 같은 순간에 생기면 변화로 잡히지 않아 그냥 지나간다.
 *
 * 그래서 비어 있을 때 자리를 차지하면 안 되고, 위 간격도 `Stack` 의 gap 이 아니라
 * 안쪽 알림이 들고 있어야 한다 — gap 은 빈 영역에도 붙는다.
 */
const LiveRegion = styled.div`
  width: 100%;

  > * {
    margin-top: ${spacing[12]};
  }
`;
