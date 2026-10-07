import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";

import { photosQueryKey, type MediaItem, type MediaList } from "@/entities/media";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { useMediaLike } from "./useMediaLike";

const ROOM_ID = 1;
const MEDIA_ID = 10;
const USER_ID = 20;
const TOKEN = "token";
const photosKey = photosQueryKey(ROOM_ID, USER_ID);

const media = {
  mediaId: MEDIA_ID,
  likeCount: 7,
  likedByMe: false,
} as MediaItem;

const setup = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData<MediaList>(photosKey, { items: [media] });

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const { result } = renderHook(
    () => useMediaLike({ roomId: ROOM_ID, mediaId: MEDIA_ID, userId: USER_ID, token: TOKEN }),
    { wrapper },
  );

  return { queryClient, result };
};

const currentMedia = (queryClient: QueryClient) =>
  queryClient.getQueryData<MediaList>(photosKey)?.items[0];

describe("useMediaLike", () => {
  it("PUT 응답 전에 목록 캐시의 좋아요를 먼저 반영한다", async () => {
    let respond: () => void = () => undefined;
    server.use(
      http.put(
        `${API_BASE_URL}/rooms/:roomId/media/:mediaId/likes`,
        () =>
          new Promise<Response>((resolve) => {
            respond = () => resolve(new HttpResponse(null, { status: 200 }));
          }),
      ),
    );
    const { queryClient, result } = setup();

    act(() => result.current.mutate(true));

    await waitFor(() => expect(currentMedia(queryClient)?.likedByMe).toBe(true));
    expect(currentMedia(queryClient)?.likeCount).toBe(8);
    expect(result.current.isPending).toBe(true);

    respond();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("DELETE 실패 시에도 서버 상태를 다시 조회하도록 목록을 무효화한다", async () => {
    server.use(
      http.delete(`${API_BASE_URL}/rooms/:roomId/media/:mediaId/likes`, () =>
        HttpResponse.json({ code: "LIKE_FAILED", message: "좋아요 취소 실패" }, { status: 500 }),
      ),
    );
    const { queryClient, result } = setup();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");
    queryClient.setQueryData<MediaList>(photosKey, {
      items: [{ ...media, likeCount: 8, likedByMe: true }],
    });

    act(() => result.current.mutate(false));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: photosKey, exact: true });
  });
});
