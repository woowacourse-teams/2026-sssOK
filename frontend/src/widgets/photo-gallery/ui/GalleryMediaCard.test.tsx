import { act, render, screen } from "@testing-library/react";

import type { GalleryItem, MediaItem } from "@/entities/media";
import { GalleryMediaCard } from "./GalleryMediaCard";

const media: MediaItem = {
  mediaId: 1,
  type: "IMAGE",
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
  size: 100,
  thumbnailUrl: "https://cdn.example.com/photo-thumbnail.jpg",
  originalUrl: "https://cdn.example.com/photo.jpg",
  width: 100,
  height: 100,
  duration: null,
  folderIds: [10],
  uploaderId: 1,
  uploaderName: "사용자",
  status: "READY",
  uploadedAt: "2026-09-28T00:00:00Z",
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

describe("GalleryMediaCard", () => {
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
      <GalleryMediaCard item={localItem} userId={1} isSelected={false} onToggle={jest.fn()} />,
    );
    const preview = screen.getByRole("img", { name: media.fileName });

    rerender(
      <GalleryMediaCard item={serverItem} userId={1} isSelected={false} onToggle={jest.fn()} />,
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
