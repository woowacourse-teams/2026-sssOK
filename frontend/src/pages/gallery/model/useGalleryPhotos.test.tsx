import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";

import type { PhotoFilter } from "@/entities/media";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { useGalleryPhotos } from "./useGalleryPhotos";

const HOST_ID = 10234;

const createWrapper = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return function QueryClientTestWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
};

const renderGalleryPhotos = (initial: {
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
}) =>
  renderHook(
    (filter) =>
      useGalleryPhotos({
        roomId: MOCK_ROOM_ID,
        accessToken: `mock-token-${HOST_ID}`,
        userId: HOST_ID,
        ...filter,
      }),
    { initialProps: initial, wrapper: createWrapper() },
  );

describe("useGalleryPhotos", () => {
  afterEach(() => server.events.removeAllListeners());

  it("방의 사진을 페이지 없이 한 번에 받는다", async () => {
    const { result } = renderGalleryPhotos({ selectedFolderId: null, selectedOption: "all" });

    await waitFor(() => expect(result.current.photos).toHaveLength(33));
    expect(new Set(result.current.photos.map((photo) => photo.mediaId)).size).toBe(33);
  });

  it("필터를 바꿔도 다시 부르지 않고 받아 둔 목록에서 거른다", async () => {
    const listRequests: string[] = [];
    server.events.on("request:start", ({ request }) => {
      if (request.url.includes("/media/all")) listRequests.push(request.url);
    });
    const { result, rerender } = renderGalleryPhotos({
      selectedFolderId: null,
      selectedOption: "all",
    });

    await waitFor(() => expect(result.current.photos).toHaveLength(33));

    rerender({ selectedFolderId: null, selectedOption: "others" });
    expect(result.current.photos.length).toBeGreaterThan(0);
    expect(result.current.photos.every((photo) => photo.uploaderId !== HOST_ID)).toBe(true);
    // 오래된 사진(4999 이하)도 대상이다 — 일부만 받아 둔 목록에서 거른 게 아니다.
    expect(result.current.photos.some((photo) => photo.mediaId < 5000)).toBe(true);

    rerender({ selectedFolderId: 32, selectedOption: "others" });
    expect(result.current.photos.map((photo) => photo.mediaId)).toEqual([5006]);

    rerender({ selectedFolderId: null, selectedOption: "mine" });
    expect(result.current.photos.every((photo) => photo.uploaderId === HOST_ID)).toBe(true);

    expect(listRequests).toHaveLength(1);
  });
});
