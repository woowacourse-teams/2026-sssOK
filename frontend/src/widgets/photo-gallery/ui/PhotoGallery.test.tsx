import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { MediaItem } from "@/entities/media";
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
});
