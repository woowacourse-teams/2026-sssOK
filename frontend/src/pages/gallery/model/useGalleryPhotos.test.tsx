import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";

import type { PhotoFilter } from "@/entities/media";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
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
  it("다음 페이지를 불러오면 이어 붙이고, 끝나면 더 부르지 않는다", async () => {
    const { result } = renderGalleryPhotos({ selectedFolderId: null, selectedOption: "all" });

    await waitFor(() => expect(result.current.photos).toHaveLength(30));
    expect(result.current.hasNextPage).toBe(true);
    expect(result.current.totalCount).toBe(33);

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.photos).toHaveLength(33));
    expect(result.current.hasNextPage).toBe(false);
    expect(new Set(result.current.photos.map((photo) => photo.mediaId)).size).toBe(33);
  });

  it("필터를 서버에 맡겨 첫 페이지 밖의 사진까지 대상으로 삼는다", async () => {
    const { result, rerender } = renderGalleryPhotos({
      selectedFolderId: null,
      selectedOption: "all",
    });

    await waitFor(() => expect(result.current.hasNextPage).toBe(true));

    rerender({ selectedFolderId: null, selectedOption: "others" });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    await waitFor(() =>
      expect(result.current.photos.every((photo) => photo.uploaderId !== HOST_ID)).toBe(true),
    );
    // 오래된 사진(4999 이하)도 첫 페이지에 들어와 있다 — 받아 둔 30장 안에서 거른 게 아니다.
    expect(result.current.photos.some((photo) => photo.mediaId < 5000)).toBe(true);
    expect(result.current.hasNextPage).toBe(false);
  });

  it("필터를 바꾼 뒤 다음 페이지를 불러도 이전 커서를 쓰지 않는다", async () => {
    const { result, rerender } = renderGalleryPhotos({
      selectedFolderId: null,
      selectedOption: "all",
    });

    await waitFor(() => expect(result.current.hasNextPage).toBe(true));

    rerender({ selectedFolderId: null, selectedOption: "mine" });
    await waitFor(() =>
      expect(result.current.photos.every((photo) => photo.uploaderId === HOST_ID)).toBe(true),
    );
    await waitFor(() => expect(result.current.isPending).toBe(false));

    rerender({ selectedFolderId: null, selectedOption: "all" });
    await waitFor(() => expect(result.current.photos).toHaveLength(30));

    act(() => result.current.loadMore());

    await waitFor(() => expect(result.current.photos).toHaveLength(33));
    expect(result.current.isError).toBe(false);
  });
});
