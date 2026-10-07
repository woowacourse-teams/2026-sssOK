import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

import type { GalleryItem, MediaItem } from "@/entities/media";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { useGallerySearch } from "./useGallerySearch";

const HOST_ID = 10234;

const media = (mediaId: number) => ({ mediaId }) as MediaItem;
const item = (mediaId: number): GalleryItem => ({
  mediaId,
  type: "server",
  media: media(mediaId),
  folderIds: [],
});

const createWrapper = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return function QueryClientTestWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
};

const renderGallerySearch = (items: GalleryItem[], allItems: GalleryItem[] = items) =>
  renderHook(
    () =>
      useGallerySearch({
        roomId: MOCK_ROOM_ID,
        accessToken: `mock-token-${HOST_ID}`,
        userId: HOST_ID,
        items,
        allItems,
      }),
    { wrapper: createWrapper() },
  );

const respondWith = (mediaIds: number[]) =>
  server.use(
    http.get(`${API_BASE_URL}/rooms/:roomId/media/search`, () =>
      HttpResponse.json({
        data: mediaIds.map((mediaId, index) => ({
          media: media(mediaId),
          similarity: 0.9 - index,
        })),
      }),
    ),
  );

describe("useGallerySearch", () => {
  it("검색 전에는 거른 사진을 그대로 보여주고 서버를 부르지 않는다", () => {
    const items = [item(3), item(2), item(1)];
    const { result } = renderGallerySearch(items);

    expect(result.current.isSearching).toBe(false);
    expect(result.current.items).toBe(items);
    expect(result.current.isPending).toBe(false);
  });

  it("필터와 상관없이 방 전체 사진 중 결과에 든 것을 유사도 순으로 보여준다", async () => {
    // 4 는 다른 폴더라 필터된 목록에는 없지만 방에는 있는 사진이고, 9 는 방 목록에 없는 사진이다.
    respondWith([1, 4, 9, 3]);
    const { result } = renderGallerySearch(
      [item(3), item(1)],
      [item(4), item(3), item(2), item(1)],
    );

    act(() => {
      result.current.openSearch();
      result.current.search("바다에서 찍은 사진");
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.items.map((slot) => slot.mediaId)).toEqual([1, 4, 3]);
  });

  it("검색을 닫으면 검색어를 비우고 원래 사진으로 돌아간다", async () => {
    respondWith([1]);
    const items = [item(2), item(1)];
    const { result } = renderGallerySearch(items);

    act(() => {
      result.current.openSearch();
      result.current.search("바다");
    });
    await waitFor(() => expect(result.current.items).toHaveLength(1));

    act(() => result.current.closeSearch());

    expect(result.current.isSearchOpen).toBe(false);
    expect(result.current.query).toBe("");
    expect(result.current.items).toBe(items);
  });

  it("검색 기능이 꺼져 있으면 쓸 수 없다고 알려준다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/rooms/:roomId/media/search`, () =>
        HttpResponse.json(
          { code: "IMAGE_SEARCH_UNAVAILABLE", message: "unavailable" },
          { status: 503 },
        ),
      ),
    );
    const { result } = renderGallerySearch([item(1)]);

    act(() => result.current.search("바다"));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.items).toEqual([]);
    expect(result.current.errorMessage).toBe("지금은 사진 검색을 쓸 수 없어요.");
  });
});
