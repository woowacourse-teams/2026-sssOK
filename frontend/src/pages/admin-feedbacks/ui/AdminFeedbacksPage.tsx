import { useCallback, useEffect, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";

import { readValidAdminSession, removeAdminSession } from "@/entities/admin-session";
import { FeedbackItem, useAdminFeedbacksQuery } from "@/entities/feedback";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { Button } from "@/shared/ui/button";
import { useInfiniteScroll } from "../model/useInfiniteScroll";
import { useVersionFilter } from "../model/useVersionFilter";
import { List, LoadingMore, Notice, Page, Sentinel, Title } from "./AdminFeedbacksPage.styles";
import { VersionFilter } from "./VersionFilter";

export const AdminFeedbacksPage = () => {
  const navigate = useNavigate();
  // AdminRoute 가 이미 걸러 주지만, 세션은 화면이 떠 있는 동안에도 만료될 수 있다.
  const session = readValidAdminSession();

  const { data, error, isPending, isFetchingNextPage, hasNextPage, fetchNextPage, refetch } =
    useAdminFeedbacksQuery(session?.accessToken ?? "");

  const feedbacks = useMemo(() => data?.pages.flatMap((page) => page.feedbacks) ?? [], [data]);
  const { versions, selectedVersion, visibleFeedbacks, toggleVersion } =
    useVersionFilter(feedbacks);

  const sentinel = useRef<HTMLDivElement>(null);
  const loadMore = useCallback(() => {
    void fetchNextPage();
  }, [fetchNextPage]);

  useInfiniteScroll({
    target: sentinel,
    // 불러오는 중에는 끈다 — 켜 두면 같은 커서로 여러 번 부른다.
    enabled: hasNextPage && !isFetchingNextPage,
    onReach: loadMore,
  });

  const isUnauthorized = isApiError(error) && error.status === 401;
  // 권한이 없는 계정은 다시 눌러도 같은 답이 온다 — 재시도 버튼을 주지 않는다.
  const isForbidden = isApiError(error) && error.status === 403;

  /*
   * 401 은 이 토큰으로 무엇을 불러도 같은 답이 온다. 세션을 버리고 로그인부터 다시 밟게 한다.
   *
   * 방 세션의 `useUnauthorizedRecovery` 가 대신해 주지 않는다 — 그쪽은 토큰으로 방을 찾아
   * 입장 화면으로 되돌리는 흐름이라, 관리자 토큰은 찾을 방이 없어 그냥 지나간다.
   */
  useEffect(() => {
    if (!isUnauthorized) return;

    removeAdminSession();
    navigate(ROUTES.adminLogin, { replace: true });
  }, [isUnauthorized, navigate]);

  return (
    <Page>
      <Title>의견</Title>

      <VersionFilter
        versions={versions}
        selectedVersion={selectedVersion}
        onToggle={toggleVersion}
      />

      {isPending && <Notice>의견을 불러오고 있어요.</Notice>}

      {/* 로그인 화면으로 옮기는 중이라 여기서 또 안내하지 않는다 — 한 프레임 비쳤다 사라진다. */}
      {error !== null && !isUnauthorized && (
        <Notice>
          {isForbidden ? "이 계정으로는 의견을 볼 수 없어요." : "의견을 불러오지 못했어요."}
          {!isForbidden && (
            <Button size="sm" variant="default" onClick={() => void refetch()}>
              다시 시도
            </Button>
          )}
        </Notice>
      )}

      {!isPending && error === null && feedbacks.length === 0 && (
        <Notice>아직 들어온 의견이 없어요.</Notice>
      )}

      <List>
        {visibleFeedbacks.map((feedback) => (
          <FeedbackItem
            key={feedback.feedbackId}
            feedback={feedback}
          />
        ))}
      </List>

      {isFetchingNextPage && <LoadingMore>더 불러오는 중…</LoadingMore>}

      <Sentinel ref={sentinel} />
    </Page>
  );
};
