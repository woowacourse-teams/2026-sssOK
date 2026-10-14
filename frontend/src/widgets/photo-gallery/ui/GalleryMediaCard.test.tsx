import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render as testingRender, screen } from "@testing-library/react";

import type { GalleryItem, MediaItem } from "@/entities/media";
import { GalleryMediaCard } from "./GalleryMediaCard";

const media: MediaItem = {
  mediaId: 1,
  type: "IMAGE",
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
  size: 100,
  thumbnailUrl: "https://cdn.example.com/photo-thumbnail.jpg",
  displayUrl: "https://cdn.example.com/photo.jpg",
  width: 100,
  height: 100,
  duration: null,
  folderIds: [10],
  uploaderId: 1,
  uploaderName: "사용자",
  status: "READY",
  uploadedAt: "2026-09-28T00:00:00Z",
  likeCount: 7,
  likedByMe: true,
};

const localItem: GalleryItem = {
  type: "local",
  mediaId: media.mediaId,
  file: new File(["photo"], media.fileName, { type: media.mimeType }),
  folderIds: media.folderIds,
};

const serverItem: GalleryItem = {
  type: "server",
  mediaId: media.mediaId,
  media,
  folderIds: media.folderIds,
};

const render = (ui: ReactNode) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return testingRender(ui, { wrapper: Wrapper });
};

describe("GalleryMediaCard", () => {
  it("목록에서 받은 좋아요 수와 상태를 표시한다", () => {
    render(
      <GalleryMediaCard
        item={serverItem}
        roomId={1}
        userId={2}
        token="token"
        isSelected={false}
        onToggle={jest.fn()}
      />,
    );

    const likeButton = screen.getByRole("button", { name: "photo.jpg 좋아요 취소" });
    expect(likeButton).toHaveAttribute("aria-pressed", "true");
    expect(likeButton).toHaveTextContent("7");
  });

  it("업로드 중인 로컬 아이템에는 좋아요 버튼을 표시하지 않는다", () => {
    const localVideoItem: GalleryItem = {
      ...localItem,
      file: new File(["video"], "video.mp4", { type: "video/mp4" }),
    };

    render(
      <GalleryMediaCard
        item={localVideoItem}
        roomId={1}
        userId={1}
        token="token"
        isSelected={false}
        onToggle={jest.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: /좋아요/ })).not.toBeInTheDocument();
  });

  it("처리 중 사진은 media.created의 displayUrl로 표시한다", () => {
    const processingItem: GalleryItem = {
      type: "server",
      mediaId: media.mediaId,
      media: {
        ...media,
        thumbnailUrl: null,
        width: null,
        height: null,
        status: "PROCESSING",
      },
      folderIds: media.folderIds,
    };

    render(
      <GalleryMediaCard
        item={processingItem}
        roomId={1}
        userId={2}
        token="token"
        isSelected={false}
        onToggle={jest.fn()}
      />,
    );

    expect(screen.getByRole("img", { name: media.fileName })).toHaveAttribute(
      "src",
      media.displayUrl,
    );
  });

  it("서버 썸네일이 준비될 때까지 같은 카드에서 로컬 미리보기를 유지한다", () => {
    const originalImage = globalThis.Image;
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    const thumbnailImage: { onload: (() => void) | null } = { onload: null };

    class MockImage {
      src = "";

      get onload() {
        return thumbnailImage.onload;
      }

      set onload(handler: (() => void) | null) {
        thumbnailImage.onload = handler;
      }
    }

    Object.defineProperty(globalThis, "Image", { configurable: true, value: MockImage });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: jest.fn(() => "blob:local-preview"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: jest.fn(),
    });

    const { rerender, unmount } = render(
      <GalleryMediaCard
        item={localItem}
        roomId={1}
        userId={1}
        token="token"
        isSelected={false}
        onToggle={jest.fn()}
      />,
    );
    const preview = screen.getByRole("img", { name: media.fileName });

    rerender(
      <GalleryMediaCard
        item={serverItem}
        roomId={1}
        userId={1}
        token="token"
        isSelected={false}
        onToggle={jest.fn()}
      />,
    );

    expect(screen.getByRole("img", { name: media.fileName })).toBe(preview);
    expect(preview).toHaveAttribute("src", "blob:local-preview");

    act(() => thumbnailImage.onload?.());

    expect(screen.getByRole("img", { name: media.fileName })).toBe(preview);
    expect(preview).toHaveAttribute("src", media.thumbnailUrl);

    unmount();
    Object.defineProperty(globalThis, "Image", { configurable: true, value: originalImage });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: originalCreateObjectURL,
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: originalRevokeObjectURL,
    });
  });
});
