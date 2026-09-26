import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import type { GalleryItem } from "@/entities/media";
import { prefersShareSheet } from "@/features/download-media/lib/prefersShareSheet";
import { saveBlob } from "@/features/download-media/lib/saveBlob";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { MediaViewerModal } from "./MediaViewerModal";

jest.mock("@/features/download-media/lib/saveBlob", () => ({ saveBlob: jest.fn() }));
jest.mock("@/features/download-media/lib/prefersShareSheet", () => ({
  prefersShareSheet: jest.fn(() => false),
}));

const TOKEN = "mock-token-10234";
const MEDIA_ID = 5000;
const prefersShareSheetMock = prefersShareSheet as jest.MockedFunction<typeof prefersShareSheet>;
const saveBlobMock = saveBlob as jest.MockedFunction<typeof saveBlob>;

const item: GalleryItem = {
  mediaId: MEDIA_ID,
  type: "server",
  folderIds: [],
  media: {
    mediaId: MEDIA_ID,
    type: "IMAGE",
    fileName: "IMG_5000.jpg",
    mimeType: "image/jpeg",
    size: 100,
    thumbnailUrl: "https://example.com/thumb.jpg",
    originalUrl: "https://example.com/original.jpg",
    width: 100,
    height: 100,
    duration: null,
    folderIds: [],
    uploaderId: 1,
    uploaderName: "쏙",
    status: "READY",
    uploadedAt: "2026-09-26T00:00:00Z",
  },
};

const serveSingle = () =>
  server.use(
    http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, ({ params }) =>
      HttpResponse.json({
        data: {
          files: [
            {
              mediaId: MEDIA_ID,
              fileName: "IMG_5000.jpg",
              downloadUrl: `${API_BASE_URL}/rooms/${params.roomId}/downloads/media/${MEDIA_ID}`,
              expiresAt: new Date(Date.now() + 300_000).toISOString(),
            },
          ],
        },
      }),
    ),
    http.get(
      `${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`,
      () => new HttpResponse(new Blob(["bytes"]), { headers: { "Content-Type": "image/jpeg" } }),
    ),
  );

/** iOS 공유 시트 자리. 넘긴 구현대로 열리거나 거절된다. */
const mockShare = (share: (data: { files: File[] }) => Promise<void>) => {
  const shareMock = jest.fn(share);

  Object.defineProperty(navigator, "canShare", { value: () => true, configurable: true });
  Object.defineProperty(navigator, "share", { value: shareMock, configurable: true });

  return shareMock;
};

const domException = (name: string) => Object.assign(new Error(name), { name });

const renderViewer = () => {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  render(
    <MediaViewerModal
      items={[item]}
      totalCount={1}
      activeMediaId={MEDIA_ID}
      roomId={MOCK_ROOM_ID}
      userId={1}
      hostId={1}
      token={TOKEN}
      selectedPhotoIds={[]}
      hasNextPage={false}
      isFetchingNextPage={false}
      isNextPageError={false}
      onLoadNextPage={jest.fn()}
      onChange={jest.fn()}
      onClose={jest.fn()}
      onToggle={jest.fn()}
      onDeleted={jest.fn()}
    />,
    { wrapper },
  );
};

describe("라이트박스 단일 다운로드", () => {
  beforeEach(() => {
    serveSingle();
    saveBlobMock.mockClear();
  });

  afterEach(() => {
    Object.defineProperty(navigator, "canShare", { value: undefined, configurable: true });
    Object.defineProperty(navigator, "share", { value: undefined, configurable: true });
  });

  it("공유 시트로 저장하는 기기에서는 파일로 받지 않고 공유 시트를 연다", async () => {
    prefersShareSheetMock.mockReturnValue(true);
    const share = mockShare(async () => {});
    renderViewer();

    await userEvent.click(screen.getByRole("button", { name: "사진 다운로드" }));

    await waitFor(() => expect(share).toHaveBeenCalledTimes(1));
    expect(share.mock.calls[0][0].files[0].name).toBe("IMG_5000.jpg");
    expect(saveBlobMock).not.toHaveBeenCalled();
  });

  it("공유 시트를 그냥 닫으면 오류 문구를 띄우지 않는다", async () => {
    prefersShareSheetMock.mockReturnValue(true);
    const share = mockShare(async () => {
      throw domException("AbortError");
    });
    renderViewer();

    await userEvent.click(screen.getByRole("button", { name: "사진 다운로드" }));

    await waitFor(() => expect(share).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "사진 다운로드" })).toBeEnabled(),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("공유 시트를 쓰지 않는 기기에서는 지금처럼 파일로 바로 저장한다", async () => {
    prefersShareSheetMock.mockReturnValue(false);
    const share = mockShare(async () => {});
    renderViewer();

    await userEvent.click(screen.getByRole("button", { name: "사진 다운로드" }));

    await waitFor(() => expect(saveBlobMock).toHaveBeenCalledTimes(1));
    expect(share).not.toHaveBeenCalled();
  });
});
