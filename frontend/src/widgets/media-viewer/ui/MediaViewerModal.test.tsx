import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import type { GalleryItem, MediaItem } from "@/entities/media";
import { prefersShareSheet } from "@/features/download-media/lib/prefersShareSheet";
import { saveBlob } from "@/features/download-media/lib/saveBlob";
import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { MediaViewerModal } from "./MediaViewerModal";

const ROOM_ID = 1;
const USER_ID = 7;
const DELETE_URL = `${API_BASE_URL}/rooms/${ROOM_ID}/media/:mediaId`;

const mediaOf = (mediaId: number, fileName: string): MediaItem => ({
  mediaId,
  type: "IMAGE",
  fileName,
  mimeType: "image/jpeg",
  size: 1024,
  thumbnailUrl: `https://example.com/${mediaId}/thumbnail.webp`,
  originalUrl: `https://example.com/${mediaId}/original.jpg`,
  width: 100,
  height: 100,
  duration: null,
  folderIds: [],
  uploaderId: USER_ID,
  uploaderName: "나",
  status: "READY",
  uploadedAt: "2026-09-20T10:00:00Z",
});

const items: GalleryItem[] = [
  { mediaId: 11, type: "server", media: mediaOf(11, "first.jpg"), folderIds: [] },
  { mediaId: 12, type: "server", media: mediaOf(12, "second.jpg"), folderIds: [] },
];

const renderViewer = () => {
  const onClose = jest.fn();
  const onChange = jest.fn();
  const onDeleted = jest.fn();
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <MediaViewerModal
        items={items}
        activeMediaId={11}
        roomId={ROOM_ID}
        userId={USER_ID}
        hostId={99}
        token="token"
        selectedPhotoIds={[]}
        onChange={onChange}
        onClose={onClose}
        onToggle={jest.fn()}
        onDeleted={onDeleted}
      />
    </QueryClientProvider>,
  );

  return { onClose, onChange, onDeleted };
};

describe("MediaViewerModal 삭제", () => {
  it("삭제 버튼을 누르면 바로 지우지 않고 뷰어 안에 확인 모달을 띄운다", async () => {
    const user = userEvent.setup();
    const deleteRequest = jest.fn();
    server.use(http.delete(DELETE_URL, deleteRequest));
    const { onDeleted } = renderViewer();

    await user.click(screen.getByRole("button", { name: "사진 삭제" }));

    const heading = screen.getByRole("heading", { name: "사진을 삭제할까요?" });
    expect(heading).toBeInTheDocument();
    expect(screen.getByLabelText("사진 크게 보기")).toContainElement(heading);
    expect(deleteRequest).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
  });

  it("확인 모달에서 삭제하기를 누르면 보고 있던 사진을 지운다", async () => {
    const user = userEvent.setup();
    const deletedIds: string[] = [];
    server.use(
      http.delete(DELETE_URL, ({ params }) => {
        deletedIds.push(String(params.mediaId));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { onDeleted } = renderViewer();

    await user.click(screen.getByRole("button", { name: "사진 삭제" }));
    await user.click(screen.getByRole("button", { name: "삭제하기" }));

    await waitFor(() => expect(onDeleted).toHaveBeenCalledWith(11));
    expect(deletedIds).toEqual(["11"]);
  });

  it("취소하면 확인 모달만 닫히고 뷰어는 같은 사진에 남는다", async () => {
    const user = userEvent.setup();
    const { onClose, onDeleted } = renderViewer();

    await user.click(screen.getByRole("button", { name: "사진 삭제" }));
    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("heading", { name: "사진을 삭제할까요?" })).not.toBeInTheDocument();
    expect(screen.getByText("1 / 2")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
  });

  it("확인 모달이 떠 있을 때 ESC 는 확인 모달만 닫는다", async () => {
    const user = userEvent.setup();
    const { onClose } = renderViewer();

    await user.click(screen.getByRole("button", { name: "사진 삭제" }));
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("heading", { name: "사진을 삭제할까요?" })).not.toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("확인 모달이 떠 있을 때 방향키로 다른 사진으로 넘어가지 않는다", async () => {
    const user = userEvent.setup();
    const { onChange } = renderViewer();

    await user.click(screen.getByRole("button", { name: "사진 삭제" }));
    await user.keyboard("{ArrowRight}");

    expect(onChange).not.toHaveBeenCalled();
  });

  it("삭제에 실패하면 확인 모달 안에 에러를 보여주고 사진을 남겨둔다", async () => {
    const user = userEvent.setup();
    server.use(
      http.delete(DELETE_URL, () =>
        HttpResponse.json(
          { code: "MEDIA_FORBIDDEN", message: "다른 사람이 올린 파일이라 삭제할 수 없습니다" },
          { status: 403 },
        ),
      ),
    );
    const { onDeleted } = renderViewer();

    await user.click(screen.getByRole("button", { name: "사진 삭제" }));
    await user.click(screen.getByRole("button", { name: "삭제하기" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("다른 사람이 올린 파일이라 삭제할 수 없습니다");
    expect(screen.getByRole("heading", { name: "사진을 삭제할까요?" })).toBeInTheDocument();
    expect(onDeleted).not.toHaveBeenCalled();
  });
});

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
