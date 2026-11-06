import { Outlet, useParams } from "react-router-dom";

import { useRoomSession } from "@/entities/member-session";
import { useLinkCodeSession } from "@/features/link-device";
import { PhotoSelectionProvider } from "@/features/select-media";
import { PageSpinner } from "@/shared/ui/spinner";

export const RoomMediaLayout = () => {
  const { code = "" } = useParams();
  const { isLinking } = useLinkCodeSession(code);

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
