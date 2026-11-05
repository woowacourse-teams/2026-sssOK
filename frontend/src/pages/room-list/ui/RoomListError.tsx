import type { FallbackProps } from "react-error-boundary";

import { Button } from "@/shared/ui/button";
import { ErrorBox } from "./RoomListPage.styles";

export const RoomListError = ({ resetErrorBoundary }: FallbackProps) => (
  <ErrorBox>
    <p>방 목록을 불러오지 못했어요.</p>
    <Button variant="default" onClick={() => resetErrorBoundary()}>
      다시 불러오기
    </Button>
  </ErrorBox>
);
