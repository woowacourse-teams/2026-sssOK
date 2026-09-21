import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { MediaItem } from "@/entities/media";
import { triggerIntersection } from "@/mocks/intersectionObserver";
import { PhotoGallery } from "./PhotoGallery";

const photo: MediaItem = {
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
  folderIds: [501],
  uploaderId: 12,
  uploaderName: "로지",
  status: "READY",
  uploadedAt: "2026-08-18T20:15:00+09:00",
};

describe("PhotoGallery", () => {
  it("체크 버튼을 클릭하면 선택할 사진 ID를 전달한다", async () => {
    const user = userEvent.setup();
    const onTogglePhoto = jest.fn();
    render(
      <PhotoGallery
        photos={[photo]}
        userId={12}
        selectedPhotoIds={[]}
        isPending={false}
        isError={false}
        onTogglePhoto={onTogglePhoto}
      />,
    );

    await user.click(screen.getByRole("button", { name: "IMG_0421.jpg 선택" }));

    expect(onTogglePhoto).toHaveBeenCalledWith(5012);
  });

  const renderWithMore = (props: { hasMore: boolean; isLoadingMore: boolean }) => {
    const onLoadMore = jest.fn();

    render(
      <PhotoGallery
        photos={[photo]}
        userId={12}
        selectedPhotoIds={[]}
        isPending={false}
        isError={false}
        onTogglePhoto={jest.fn()}
        onLoadMore={onLoadMore}
        {...props}
      />,
    );

    return onLoadMore;
  };

  it("목록 끝이 보이면 다음 페이지를 부른다", () => {
    const onLoadMore = renderWithMore({ hasMore: true, isLoadingMore: false });

    triggerIntersection();

    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });

  it("불러오는 중이거나 더 받을 것이 없으면 부르지 않는다", () => {
    const whileLoading = renderWithMore({ hasMore: true, isLoadingMore: true });
    const atEnd = renderWithMore({ hasMore: false, isLoadingMore: false });

    triggerIntersection();

    expect(whileLoading).not.toHaveBeenCalled();
    expect(atEnd).not.toHaveBeenCalled();
    expect(screen.getByText("사진을 더 불러오는 중이에요.")).toBeInTheDocument();
  });
});
