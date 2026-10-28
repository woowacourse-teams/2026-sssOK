import { Outlet, useParams } from "react-router-dom";

import { useRoomSession } from "@/entities/member-session";
import { useLinkCodeSession } from "@/features/link-device";
import { PhotoSelectionProvider } from "@/features/select-media";
import { PageSpinner } from "@/shared/ui/spinner";
import { useUnauthorizedRecovery } from "./useUnauthorizedRecovery";

export const RoomMediaLayout = () => {
  const { code = "" } = useParams();
  const { isLinking } = useLinkCodeSession(code);

  // 토큰을 들고 부르는 화면은 모두 이 아래에 있다. 여기 한 번 걸어두면 갤러리·상세·설정이
  // 각자 401 을 알아볼 필요가 없다 (#149).
  useUnauthorizedRecovery();

  const { session, isPending, isError, refetch } = useRoomSession(code);

  if (isPending) return <PageSpinner label="로그인 정보를 불러오는 중이에요." />;
  if (isError)
    return (
      <main>
        <p>로그인 정보를 확인하지 못했어요.</p>
        <button onClick={() => void refetch()}>다시 시도</button>
      </main>
    );

  if (isLinking) return <PageSpinner label="다른 기기와 연결하는 중이에요." />;

  return (
    <PhotoSelectionProvider key={`${code}:${session?.userId ?? "guest"}`}>
      <Outlet />
    </PhotoSelectionProvider>
  );
};
