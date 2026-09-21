import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";

import type { RoomFolder } from "@/entities/room";
import { track } from "@/shared/lib/analytics";
import { GalleryModalContext } from "./galleryModalContext";
import { useCreateFolderAction } from "./useCreateFolderAction";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));

const FOLDER: RoomFolder = {
  id: 7,
  name: "1일차",
  createdAt: "2026-09-21T00:00:00Z",
  photoCount: 0,
};

/** 모달이 어떤 결말로 닫힐지 정해두고 훅을 띄운다 */
const renderWithModalResult = (result: RoomFolder | null) => {
  const queryClient = new QueryClient();
  const openModal = jest.fn().mockResolvedValue(result);

  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <GalleryModalContext.Provider value={{ activeModal: null, openModal, closeModal: jest.fn() }}>
        {children}
      </GalleryModalContext.Provider>
    </QueryClientProvider>
  );

  return renderHook(
    () =>
      useCreateFolderAction({
        roomCode: "7K93QX2S",
        userId: 10234,
        selectFolder: jest.fn(),
        clearSelection: jest.fn(),
      }),
    { wrapper: Wrapper },
  );
};

describe("useCreateFolderAction 폴더 추가 경로 이벤트", () => {
  it.each(["menu", "filter_plus"] as const)(
    "%s 에서 폴더를 만들면 시작과 완료를 같은 진입점으로 남긴다",
    async (source) => {
      const { result } = renderWithModalResult(FOLDER);

      await act(() => result.current.handleCreateFolder(source));

      expect(track).toHaveBeenCalledWith("Folder Create Started", { source });
      expect(track).toHaveBeenCalledWith("Folder Created", { source });
    },
  );

  it("모달을 닫고 만들지 않으면 시작만 남긴다", async () => {
    const { result } = renderWithModalResult(null);

    await act(() => result.current.handleCreateFolder("menu"));

    expect(track).toHaveBeenCalledWith("Folder Create Started", { source: "menu" });
    expect(track).not.toHaveBeenCalledWith("Folder Created", expect.anything());
  });
});
