import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { GalleryItem, MediaItem } from "@/entities/media";
import { MediaViewerModal } from "./MediaViewerModal";

const photoOf = (mediaId: number): GalleryItem => {
  const media: MediaItem = {
    mediaId,
    type: "IMAGE",
    fileName: `IMG_${mediaId}.jpg`,
    mimeType: "image/jpeg",
    size: 3840219,
    thumbnailUrl: `https://cdn.example.com/rooms/1024/${mediaId}_thumb.webp`,
    originalUrl: `https://cdn.example.com/rooms/1024/${mediaId}.jpg`,
    width: 4032,
    height: 3024,
    duration: null,
    folderIds: [],
    uploaderId: 12,
    uploaderName: "로지",
    status: "READY",
    uploadedAt: "2026-08-18T20:15:00+09:00",
  };

  return { mediaId, type: "server", media, folderIds: [] };
};

const items = [photoOf(1), photoOf(2), photoOf(3)];

const renderViewer = (activeMediaId: number) => {
  const onChange = jest.fn();

  render(
    <QueryClientProvider client={new QueryClient()}>
      <MediaViewerModal
        items={items}
        activeMediaId={activeMediaId}
        roomId={1024}
        userId={12}
        hostId={12}
        token="token"
        selectedPhotoIds={[]}
        onChange={onChange}
        onClose={jest.fn()}
        onToggle={jest.fn()}
        onDeleted={jest.fn()}
      />
    </QueryClientProvider>,
  );

  return { onChange };
};

describe("MediaViewerModal", () => {
  it("사진 왼쪽 절반을 누르면 이전 사진으로 넘어간다", async () => {
    const user = userEvent.setup();
    const { onChange } = renderViewer(2);

    await user.click(screen.getByTestId("viewer-tap-previous"));

    expect(onChange).toHaveBeenCalledWith(1);
  });

  it("사진 오른쪽 절반을 누르면 다음 사진으로 넘어간다", async () => {
    const user = userEvent.setup();
    const { onChange } = renderViewer(2);

    await user.click(screen.getByTestId("viewer-tap-next"));

    expect(onChange).toHaveBeenCalledWith(3);
  });

  it("첫 사진에서 왼쪽을 눌러도 넘어가지 않는다", async () => {
    const user = userEvent.setup();
    const { onChange } = renderViewer(1);

    await user.click(screen.getByTestId("viewer-tap-previous"));

    expect(onChange).not.toHaveBeenCalled();
  });

  it("마지막 사진에서 오른쪽을 눌러도 넘어가지 않는다", async () => {
    const user = userEvent.setup();
    const { onChange } = renderViewer(3);

    await user.click(screen.getByTestId("viewer-tap-next"));

    expect(onChange).not.toHaveBeenCalled();
  });
});
