import type { FallbackProps } from "react-error-boundary";

import { Button } from "@/shared/ui/button";
import { ErrorBox } from "./RoomListPage.styles";

export const RoomListError = ({ resetErrorBoundary }: FallbackProps) => (
  <ErrorBox>
    <h2>방 목록을 불러오지 못했어요.</h2>
    <p>잠시 후 다시 불러와 주세요.</p>
    <Button variant="default" onClick={() => resetErrorBoundary()}>
      다시 불러오기
    </Button>
  </ErrorBox>
);
