import { createContext, useContext } from "react";
import type { RoomFolder } from "@/entities/room";

export type GalleryModalRequest = Omit<GalleryModal, "resolve">;

export interface GalleryModal {
  type: "create-folder";
  resolve: (result: RoomFolder | null) => void;
}

interface GalleryModalContextValue {
  activeModal: GalleryModal | null;
  openModal: (modal: GalleryModalRequest) => Promise<RoomFolder | null>;
  closeModal: () => void;
}

export const GalleryModalContext = createContext<GalleryModalContextValue | null>(null);

export const useGalleryModal = (): GalleryModalContextValue => {
  const context = useContext(GalleryModalContext);

  if (!context) {
    throw new Error("useGalleryModal은 GalleryModalProvider 내부에서 사용해야 합니다.");
  }

  return context;
};
