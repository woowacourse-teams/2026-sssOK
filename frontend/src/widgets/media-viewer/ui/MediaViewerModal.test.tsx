import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import type { GalleryItem, MediaItem } from "@/entities/media";
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
