import { useRouteError } from "react-router-dom";

import { useStartDestination } from "@/entities/member-session";
import { ErrorState, ErrorStateButton, ErrorStateLink } from "@/shared/ui/error-state";

/** 배포로 파일 이름(contenthash)이 바뀌어 이전 화면 코드를 받지 못한 경우다. */
const isChunkLoadError = (error: unknown): boolean =>
  error instanceof Error &&
  (error.name === "ChunkLoadError" || /Loading (CSS )?chunk .+ failed/.test(error.message));

export const RouteErrorPage = () => {
  const error = useRouteError();
  const isChunkError = isChunkLoadError(error);
  const start = useStartDestination();

  return (
    <ErrorState
      title={isChunkError ? "화면을 불러오지 못했어요" : "문제가 생겼어요"}
      description={
        isChunkError
          ? "새 버전이 나왔거나 연결이 불안정해요.\n새로고침하면 다시 열려요."
          : "잠시 후 다시 시도해 주세요."
      }
    >
      <ErrorStateButton onClick={() => window.location.reload()}>새로고침</ErrorStateButton>
      <ErrorStateLink to={start.to} reloadDocument={start.kind === "landing"}>
        {start.kind === "rooms" ? "내 방 목록으로 가기" : "홈으로 가기"}
      </ErrorStateLink>
    </ErrorState>
  );
};
