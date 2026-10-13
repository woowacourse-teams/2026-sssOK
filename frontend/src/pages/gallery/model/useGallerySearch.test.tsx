import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

import type { GalleryItem, MediaItem } from "@/entities/media";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { track } from "@/shared/lib/analytics";
import { useGallerySearch } from "./useGallerySearch";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));

const HOST_ID = 10234;

const media = (mediaId: number, uploaderName = "해니") => ({ mediaId, uploaderName }) as MediaItem;
const item = (mediaId: number, uploaderName?: string): GalleryItem => ({
  mediaId,
  type: "server",
  media: media(mediaId, uploaderName),
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
    http.get(`${API_BASE_URL}/rooms/:roomId/media/all`, () =>
      HttpResponse.json({ data: { items: mediaIds.map((mediaId) => media(mediaId)) } }),
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
      http.get(`${API_BASE_URL}/rooms/:roomId/media/all`, () =>
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

  describe("닉네임 검색", () => {
    it("서버를 부르지 않고 방 전체 사진 중 닉네임이 들어간 사람의 사진을 보여준다", () => {
      const searchRequest = jest.fn();
      server.use(
        http.get(`${API_BASE_URL}/rooms/:roomId/media/all`, () => {
          searchRequest();
          return HttpResponse.json({ data: { items: [] } });
        }),
      );
      // 3 은 필터된 목록에는 없지만 방에는 있는 민수의 사진이다.
      const { result } = renderGallerySearch(
        [item(2, "지우"), item(1, "민수")],
        [item(3, "민수"), item(2, "지우"), item(1, "민수")],
      );

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("민"));

      expect(result.current.items.map((slot) => slot.mediaId)).toEqual([3, 1]);
      expect(result.current.isPending).toBe(false);
      expect(result.current.isError).toBe(false);
      expect(searchRequest).not.toHaveBeenCalled();
    });

    it("내 사진은 갤러리에 '나' 로 보여도 내 닉네임으로 찾을 수 있다", () => {
      const { result } = renderGallerySearch([item(2, "지우"), item(1, "해니")]);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("해니"));

      expect(result.current.items.map((slot) => slot.mediaId)).toEqual([1]);
    });

    it("방을 나가 이름이 없는 사람의 사진은 건너뛴다", () => {
      const left = {
        ...item(2),
        media: { ...media(2), uploaderName: null },
      } as unknown as GalleryItem;
      const { result } = renderGallerySearch([left, item(1, "민수")]);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("민수"));

      expect(result.current.items.map((slot) => slot.mediaId)).toEqual([1]);
    });

    it("영문 닉네임은 대소문자를 가리지 않는다", () => {
      const { result } = renderGallerySearch([item(1, "Danny")]);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("danny"));

      expect(result.current.items).toHaveLength(1);
    });

    it("검색 기준을 바꾸면 검색어를 비우고 원래 사진으로 돌아간다", () => {
      const items = [item(2, "지우"), item(1, "민수")];
      const { result } = renderGallerySearch(items);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("민수"));
      act(() => result.current.changeSearchType("keyword"));

      expect(result.current.query).toBe("");
      expect(result.current.items).toBe(items);
    });

    it("검색을 닫으면 다음에 열 때 검색어 기준으로 돌아간다", () => {
      const { result } = renderGallerySearch([item(1)]);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.closeSearch());

      expect(result.current.searchType).toBe("keyword");
    });
  });

  describe("이벤트", () => {
    beforeEach(() => jest.mocked(track).mockClear());

    it("검색창을 열면 Search Opened 를 보낸다", () => {
      const { result } = renderGallerySearch([item(1)]);

      act(() => result.current.openSearch());

      expect(track).toHaveBeenCalledWith("Search Opened", {});
    });

    it("결과를 받으면 검색어 길이와 보이는 결과 수를 보내고 검색어 원문은 보내지 않는다", async () => {
      respondWith([1, 9]);
      const { result } = renderGallerySearch([item(2), item(1)]);

      act(() => result.current.search("바다"));

      await waitFor(() =>
        expect(track).toHaveBeenCalledWith("Photo Searched", {
          search_type: "keyword",
          query_length: 2,
          result_count: 1,
          duration_ms: expect.any(Number),
        }),
      );
      expect(JSON.stringify(jest.mocked(track).mock.calls)).not.toContain("바다");
    });

    it("같은 검색어를 다시 제출해 캐시로 답해도 다시 보낸다", async () => {
      respondWith([1]);
      const { result } = renderGallerySearch([item(1)]);

      act(() => result.current.search("바다"));
      await waitFor(() => expect(track).toHaveBeenCalledTimes(1));

      act(() => result.current.search("바다"));

      await waitFor(() => expect(track).toHaveBeenCalledTimes(2));
      expect(jest.mocked(track).mock.calls.map(([event]) => event)).toEqual([
        "Photo Searched",
        "Photo Searched",
      ]);
    });

    it("요청이 실패하면 에러 코드를 담아 Photo Search Failed 를 보낸다", async () => {
      server.use(
        http.get(`${API_BASE_URL}/rooms/:roomId/media/all`, () =>
          HttpResponse.json(
            { code: "IMAGE_SEARCH_UNAVAILABLE", message: "unavailable" },
            { status: 503 },
          ),
        ),
      );
      const { result } = renderGallerySearch([item(1)]);

      act(() => result.current.search("바다"));

      await waitFor(() =>
        expect(track).toHaveBeenCalledWith("Photo Search Failed", {
          reason: "IMAGE_SEARCH_UNAVAILABLE",
        }),
      );
      expect(track).not.toHaveBeenCalledWith("Photo Searched", expect.anything());
    });

    it("검색 결과에서 사진을 열면 결과 속 순위와 결과 수를 보낸다", async () => {
      respondWith([3, 1]);
      const { result } = renderGallerySearch([item(1), item(2), item(3)]);

      act(() => result.current.search("바다"));
      await waitFor(() => expect(result.current.items).toHaveLength(2));

      act(() => result.current.trackResultOpened(1));

      expect(track).toHaveBeenCalledWith("Search Result Opened", {
        search_type: "keyword",
        rank: 2,
        result_count: 2,
      });
    });

    it("닉네임 검색은 응답 시간 없이 검색 기준과 결과 수를 보내고 닉네임 원문은 보내지 않는다", () => {
      const { result } = renderGallerySearch([item(2, "지우"), item(1, "민수")]);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("민수"));

      expect(track).toHaveBeenCalledWith("Photo Searched", {
        search_type: "nickname",
        query_length: 2,
        result_count: 1,
      });
      expect(JSON.stringify(jest.mocked(track).mock.calls)).not.toContain("민수");
    });

    it("닉네임 검색 결과에서 사진을 열면 검색 기준을 담아 보낸다", () => {
      const { result } = renderGallerySearch([item(2, "민수"), item(1, "민수")]);

      act(() => result.current.changeSearchType("nickname"));
      act(() => result.current.search("민수"));
      act(() => result.current.trackResultOpened(1));

      expect(track).toHaveBeenCalledWith("Search Result Opened", {
        search_type: "nickname",
        rank: 2,
        result_count: 2,
      });
    });

    it("검색 중이 아닐 때 사진을 열면 보내지 않는다", () => {
      const { result } = renderGallerySearch([item(1)]);

      act(() => result.current.trackResultOpened(1));

      expect(track).not.toHaveBeenCalled();
    });
  });
});
