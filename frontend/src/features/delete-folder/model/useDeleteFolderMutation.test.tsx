import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

import { photosQueryKey, type MediaItem, type MediaList } from "@/entities/media";
import { roomQueryKey, type Room } from "@/entities/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { useDeleteFolderMutation } from "./useDeleteFolderMutation";

const ROOM_ID = 1;
const ROOM_CODE = "7K93QX2S";
const USER_ID = 10;
const FOLDER_ID = 100;
const OTHER_FOLDER_ID = 200;

const roomKey = roomQueryKey(ROOM_CODE, USER_ID);
const photosKey = photosQueryKey(ROOM_ID, USER_ID);

const room = {
  roomId: ROOM_ID,
  code: ROOM_CODE,
  folders: [
    { id: FOLDER_ID, name: "바다", createdAt: "2026-09-01T00:00:00Z", photoCount: 2 },
    { id: OTHER_FOLDER_ID, name: "산", createdAt: "2026-09-02T00:00:00Z", photoCount: 1 },
  ],
} as Room;

const photos = {
  items: [
    { mediaId: 1, folderIds: [FOLDER_ID] },
    { mediaId: 2, folderIds: [FOLDER_ID, OTHER_FOLDER_ID] },
    { mediaId: 3, folderIds: [] },
  ] as unknown as MediaItem[],
} as MediaList;

const setup = (onError = jest.fn()) => {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  queryClient.setQueryData(roomKey, room);
  queryClient.setQueryData(photosKey, photos);

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const { result } = renderHook(
    () =>
      useDeleteFolderMutation({
        roomId: ROOM_ID,
        roomCode: ROOM_CODE,
        userId: USER_ID,
        folderId: FOLDER_ID,
        accessToken: "mock-token",
        onError,
      }),
    { wrapper },
  );

  return { queryClient, result, onError };
};

const folderIdsOf = (queryClient: QueryClient) =>
  queryClient.getQueryData<MediaList>(photosKey)?.items.map((item) => item.folderIds);

describe("useDeleteFolderMutation", () => {
  it("응답을 기다리지 않고 폴더와 사진의 폴더 연결을 캐시에서 먼저 뺀다", async () => {
    let respond: () => void = () => undefined;
    server.use(
      http.delete(
        `${API_BASE_URL}/rooms/:roomId/folders/:folderId`,
        () =>
          new Promise<Response>((resolve) => {
            respond = () =>
              resolve(
                HttpResponse.json({ data: { deletedFolderId: FOLDER_ID, detachedPhotoCount: 2 } }),
              );
          }),
      ),
    );
    const { queryClient, result } = setup();

    act(() => result.current.mutate());

    await waitFor(() =>
      expect(queryClient.getQueryData<Room>(roomKey)?.folders.map(({ id }) => id)).toEqual([
        OTHER_FOLDER_ID,
      ]),
    );
    expect(folderIdsOf(queryClient)).toEqual([[], [OTHER_FOLDER_ID], []]);
    expect(result.current.isPending).toBe(true);

    respond();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("삭제에 실패하면 캐시를 되돌리고 onError 를 부른다", async () => {
    server.use(
      http.delete(`${API_BASE_URL}/rooms/:roomId/folders/:folderId`, () =>
        HttpResponse.json(
          { code: "FOLDER_NOT_FOUND", message: "존재하지 않는 폴더입니다." },
          { status: 404 },
        ),
      ),
    );
    const { queryClient, result, onError } = setup();

    act(() => result.current.mutate());

    await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(queryClient.getQueryData<Room>(roomKey)?.folders).toEqual(room.folders);
    expect(folderIdsOf(queryClient)).toEqual([[FOLDER_ID], [FOLDER_ID, OTHER_FOLDER_ID], []]);
  });
});
