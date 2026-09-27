import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { MediaItem } from "@/entities/media";
import { SPINNER_DELAY_MS } from "@/shared/ui/spinner";
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

  describe("처음 불러오는 동안", () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    const renderPending = () =>
      render(
        <PhotoGallery
          photos={[]}
          userId={12}
          selectedPhotoIds={[]}
          isPending
          isError={false}
          onTogglePhoto={jest.fn()}
        />,
      );

    it("불러오는 중임을 status 로 알린다", () => {
      renderPending();

      expect(screen.getByRole("status")).toHaveTextContent("사진을 불러오는 중이에요.");
    });

    it("잠시 뒤 카드 모양의 스켈레톤 그리드를 보여준다", () => {
      const { container } = renderPending();

      expect(container.querySelector("[aria-hidden]")).toBeNull();

      act(() => jest.advanceTimersByTime(SPINNER_DELAY_MS));

      expect(container.querySelector("[aria-hidden]")?.children.length).toBeGreaterThan(0);
    });
  });
});
