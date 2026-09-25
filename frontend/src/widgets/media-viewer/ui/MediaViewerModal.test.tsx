import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { GalleryItem } from "@/entities/media";
import { MediaViewerModal } from "./MediaViewerModal";

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
        totalCount={1}
        activeMediaId={5012}
        roomId={1024}
        userId={12}
        hostId={12}
        token="token"
        selectedPhotoIds={[]}
        hasNextPage={false}
        isFetchingNextPage={false}
        isNextPageError={false}
        onLoadNextPage={jest.fn()}
        onChange={jest.fn()}
        onClose={jest.fn()}
        onToggle={jest.fn()}
        onDeleted={jest.fn()}
      />
    </QueryClientProvider>,
  );

describe("MediaViewerModal", () => {
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
