import { Outlet, useParams } from "react-router-dom";

import { readValidRoomSession } from "@/entities/session";
import { useLinkCodeSession } from "@/features/link-device";
import { PhotoSelectionProvider } from "@/features/select-media";
import { useUnauthorizedRecovery } from "./useUnauthorizedRecovery";

export const RoomMediaLayout = () => {
  const { code = "" } = useParams();
  const { isLinking } = useLinkCodeSession(code);

  // 토큰을 들고 부르는 화면은 모두 이 아래에 있다. 여기 한 번 걸어두면 갤러리·상세·설정이
  // 각자 401 을 알아볼 필요가 없다 (#149).
  useUnauthorizedRecovery();

  const session = readValidRoomSession(code);

  if (isLinking) return null;

  return (
    <PhotoSelectionProvider key={`${code}:${session?.userId ?? "guest"}`}>
      <Outlet />
    </PhotoSelectionProvider>
  );
};
