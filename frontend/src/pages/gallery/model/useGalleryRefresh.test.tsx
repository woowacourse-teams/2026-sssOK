import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";

import { photosQueryKey } from "@/entities/media";
import { roomQueryKey } from "@/entities/room";
import { useGalleryRefresh } from "./useGalleryRefresh";

const ROOM = { roomCode: "7K93QX2S", roomId: 3, userId: 10234 };

const renderWithQueries = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const getRoom = jest.fn().mockResolvedValue({});
  const getPhotos = jest.fn().mockResolvedValue({ items: [] });

  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const view = renderHook(
    () => {
      // 갤러리가 화면에 띄워 둔 두 조회를 흉내 낸다. 활성 조회만 무효화 때 다시 불린다.
      useQuery({ queryKey: roomQueryKey(ROOM.roomCode, ROOM.userId), queryFn: getRoom });
      useQuery({ queryKey: photosQueryKey(ROOM.roomId, ROOM.userId), queryFn: getPhotos });
      return useGalleryRefresh(ROOM);
    },
    { wrapper: Wrapper },
  );

  return { ...view, getRoom, getPhotos };
};

describe("useGalleryRefresh", () => {
  it("성공 처리와 SSE 이벤트가 겹쳐도 방 정보·사진 목록을 한 번씩만 다시 받는다", async () => {
    const { result, getRoom, getPhotos } = renderWithQueries();
    await waitFor(() => expect(getPhotos).toHaveBeenCalledTimes(1));
    getRoom.mockClear();
    getPhotos.mockClear();

    await act(() =>
      Promise.all([
        // SSE media.folders.updated
        result.current.refreshRoom(),
        result.current.refreshPhotos(),
        // 폴더 이동 성공 처리
        result.current.refreshPhotos(),
        result.current.refreshRoom(),
      ]),
    );

    expect(getRoom).toHaveBeenCalledTimes(1);
    expect(getPhotos).toHaveBeenCalledTimes(1);
  });

  it("앞 요청이 끝난 뒤에 부르면 다시 받는다", async () => {
    const { result, getPhotos } = renderWithQueries();
    await waitFor(() => expect(getPhotos).toHaveBeenCalledTimes(1));
    getPhotos.mockClear();

    await act(() => result.current.refreshPhotos());
    await act(() => result.current.refreshPhotos());

    expect(getPhotos).toHaveBeenCalledTimes(2);
  });
});
