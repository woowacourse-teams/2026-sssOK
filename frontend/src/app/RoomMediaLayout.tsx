import { Outlet, useParams } from "react-router-dom";

import { useRoomSession } from "@/entities/member-session";
import { useLinkCodeSession } from "@/features/link-device";
import { PhotoSelectionProvider } from "@/features/select-media";
import { ErrorState, ErrorStateButton } from "@/shared/ui/error-state";
import { PageSpinner } from "@/shared/ui/spinner";

export const RoomMediaLayout = () => {
  const { code = "" } = useParams();
  const { isLinking } = useLinkCodeSession(code);

  const { session, isPending, isError, isFetching, refetch } = useRoomSession(code);

  if (isPending) return <PageSpinner label="로그인 정보를 불러오는 중이에요." />;
  if (isError)
    return (
      <ErrorState
        title="로그인 정보를 확인하지 못했어요."
        description="잠시 후 다시 시도해 주세요."
      >
        <ErrorStateButton
          onClick={() => void refetch()}
          disabled={isFetching}
          aria-busy={isFetching}
        >
          다시 시도
        </ErrorStateButton>
      </ErrorState>
    );

  if (isLinking) return <PageSpinner label="다른 기기와 연결하는 중이에요." />;

  return (
    <PhotoSelectionProvider key={`${code}:${session?.userId ?? "guest"}`}>
      <Outlet />
    </PhotoSelectionProvider>
  );
};
