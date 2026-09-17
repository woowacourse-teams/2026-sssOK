import { useState, type ReactNode } from "react";
import type { RoomFolder } from "@/entities/room";
import {
  GalleryModalContext,
  type GalleryModal,
  type GalleryModalRequest,
} from "../model/galleryModalContext";

interface Props {
  children: ReactNode;
}

export const GalleryModalProvider = ({ children }: Props) => {
  const [activeModal, setActiveModal] = useState<GalleryModal | null>(null);

  const openModal = (modal: GalleryModalRequest) =>
    new Promise<RoomFolder | null>((resolve) => {
      const resolveModal = (folder: RoomFolder | null) => {
        resolve(folder);
        setActiveModal(null);
      };

      setActiveModal({ ...modal, resolve: resolveModal });
    });

  const closeModal = () => {
    activeModal?.resolve(null);
  };

  return (
    <GalleryModalContext value={{ activeModal, openModal, closeModal }}>
      {children}
    </GalleryModalContext>
  );
};
