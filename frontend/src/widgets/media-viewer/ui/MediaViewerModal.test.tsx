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

describe("MediaViewerModal", () => {
  const item: GalleryItem = {
    mediaId: 5012,
    type: "server",
    folderIds: [],
    media: {
      mediaId: 5012,
      type: "IMAGE",
      fileName: "IMG_0421.jpg",
      mimeType: "image/jpeg",
      size: 3840219,
      thumbnailUrl: "https://cdn.example.com/rooms/1024/5012_thumb.webp",
      originalUrl: "https://cdn.example.com/rooms/1024/5012.jpg",
      width: 4032,
      height: 3024,
      duration: null,
      folderIds: [],
      uploaderId: 12,
      uploaderName: "로지",
      status: "READY",
      uploadedAt: "2026-08-18T20:15:00+09:00",
    },
  };

  const renderModal = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MediaViewerModal
          items={[item]}
          activeMediaId={5012}
          roomId={1024}
          userId={12}
          hostId={12}
          token="token"
          selectedPhotoIds={[]}
          onChange={jest.fn()}
          onClose={jest.fn()}
          onToggle={jest.fn()}
          onDeleted={jest.fn()}
        />
      </QueryClientProvider>,
    );

  it("열리면 첫 버튼이 아니라 모달 자체가 포커스를 받는다", () => {
    renderModal();

    expect(screen.getByRole("dialog", { name: "사진 크게 보기" })).toHaveFocus();
    expect(screen.getByRole("button", { name: "갤러리로 돌아가기" })).not.toHaveFocus();
  });

  it("Tab 을 누르면 모달 안의 첫 버튼으로 포커스가 이동한다", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.tab();

    expect(screen.getByRole("button", { name: "갤러리로 돌아가기" })).toHaveFocus();
  });

  it("닫히면 열기 전에 포커스가 있던 요소로 포커스를 돌려준다", () => {
    const trigger = document.createElement("button");
    document.body.append(trigger);
    trigger.focus();

    const { unmount } = renderModal();
    unmount();

    expect(trigger).toHaveFocus();
    trigger.remove();
  });
});

describe("라이트박스 단일 다운로드", () => {
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
        activeMediaId={MEDIA_ID}
        roomId={MOCK_ROOM_ID}
        userId={1}
        hostId={1}
        token={TOKEN}
        selectedPhotoIds={[]}
        onChange={jest.fn()}
        onClose={jest.fn()}
        onToggle={jest.fn()}
        onDeleted={jest.fn()}
      />,
      { wrapper },
    );
  };

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
