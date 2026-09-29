import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

import type { GalleryItem, MediaItem, PhotoFilter } from "@/entities/media";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { useGalleryPhotos } from "./useGalleryPhotos";

const HOST_ID = 10234;

const serverPhotosOf = (items: GalleryItem[]) =>
  items.flatMap((item) => (item.type === "server" ? [item.media] : []));

const createWrapper = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return function QueryClientTestWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
};

const renderGalleryPhotos = (initial: {
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
  uploadSlots?: GalleryItem[];
}) =>
  renderHook(
    (filter) =>
      useGalleryPhotos({
        roomId: MOCK_ROOM_ID,
        accessToken: `mock-token-${HOST_ID}`,
        userId: HOST_ID,
        initialTotalCount: 13,
        initialFolders: [
          { id: 32, name: "여름", createdAt: "2026-09-28T00:00:00Z", photoCount: 4 },
        ],
        ...filter,
      }),
    { initialProps: initial, wrapper: createWrapper() },
  );

describe("useGalleryPhotos", () => {
  afterEach(() => server.events.removeAllListeners());

  it("사진 목록을 받기 전에는 방 조회의 전체 및 폴더 개수를 사용한다", () => {
    const { result } = renderGalleryPhotos({ selectedFolderId: null, selectedOption: "all" });

    expect(result.current.totalCount).toBe(13);
    expect(result.current.folderCounts.get(32)).toBe(4);
  });

  it("방의 사진을 페이지 없이 한 번에 받는다", async () => {
    const { result } = renderGalleryPhotos({ selectedFolderId: null, selectedOption: "all" });

    await waitFor(() => expect(result.current.galleryItems).toHaveLength(33));
    expect(result.current.totalCount).toBe(33);
    expect(new Set(result.current.galleryItems.map((item) => item.mediaId)).size).toBe(33);
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

    await waitFor(() => expect(result.current.galleryItems).toHaveLength(33));
    const initialFolderCounts = result.current.folderCounts;

    rerender({ selectedFolderId: null, selectedOption: "others" });
    expect(result.current.galleryItems.length).toBeGreaterThan(0);
    expect(
      serverPhotosOf(result.current.galleryItems).every((photo) => photo.uploaderId !== HOST_ID),
    ).toBe(true);
    // 오래된 사진(4999 이하)도 대상이다 — 일부만 받아 둔 목록에서 거른 게 아니다.
    expect(result.current.galleryItems.some((item) => item.mediaId < 5000)).toBe(true);

    rerender({ selectedFolderId: 32, selectedOption: "others" });
    expect(result.current.galleryItems.map((item) => item.mediaId)).toEqual([5006]);

    rerender({ selectedFolderId: null, selectedOption: "mine" });
    expect(
      serverPhotosOf(result.current.galleryItems).every((photo) => photo.uploaderId === HOST_ID),
    ).toBe(true);
    expect(result.current.totalCount).toBe(33);
    expect(result.current.folderCounts).toEqual(initialFolderCounts);

    expect(listRequests).toHaveLength(1);
  });

  it("서버 목록에서 확인된 업로드 미리보기를 정리 대상으로 알려준다", async () => {
    const uploadedMediaId = 5006;
    const { result } = renderGalleryPhotos({
      selectedFolderId: null,
      selectedOption: "all",
      uploadSlots: [
        {
          type: "local",
          mediaId: uploadedMediaId,
          file: new File(["photo"], "photo.jpg", { type: "image/jpeg" }),
          folderIds: [32],
        },
      ],
    });

    await waitFor(() => expect(result.current.completedUploadIds).toEqual([uploadedMediaId]));
    expect(result.current.totalCount).toBe(33);
    expect(
      result.current.galleryItems.filter((item) => item.mediaId === uploadedMediaId),
    ).toHaveLength(1);
  });

  it("서버 사진의 썸네일이 준비될 때까지 업로드 미리보기를 유지한다", async () => {
    const uploadedMediaId = 5006;
    const mediaWithoutThumbnail: MediaItem = {
      mediaId: uploadedMediaId,
      folderIds: [32],
      uploaderId: HOST_ID,
      uploaderName: "사용자",
      type: "IMAGE",
      fileName: "photo.jpg",
      mimeType: "image/jpeg",
      size: 100,
      thumbnailUrl: "",
      originalUrl: "",
      width: 100,
      height: 100,
      duration: null,
      status: "READY",
      uploadedAt: "2026-09-28T00:00:00Z",
    };
    server.use(
      http.get(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media/all`, () =>
        HttpResponse.json({ data: { items: [mediaWithoutThumbnail] } }),
      ),
    );
    const { result } = renderGalleryPhotos({
      selectedFolderId: null,
      selectedOption: "all",
      uploadSlots: [
        {
          type: "local",
          mediaId: uploadedMediaId,
          file: new File(["photo"], "photo.jpg", { type: "image/jpeg" }),
          folderIds: [32],
        },
      ],
    });

    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.completedUploadIds).toEqual([]);
    expect(result.current.galleryItems.map((item) => item.mediaId)).toEqual([uploadedMediaId]);
  });
});
