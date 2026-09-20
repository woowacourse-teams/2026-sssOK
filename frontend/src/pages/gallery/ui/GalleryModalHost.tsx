import { CreateFolderBottomSheet } from "@/features/create-folder";
import { useGalleryModal } from "../model/galleryModalContext";

interface GalleryModalHostProps {
  roomId: number;
  accessToken: string;
}

export const GalleryModalHost = ({ roomId, accessToken }: GalleryModalHostProps) => {
  const { activeModal, closeModal } = useGalleryModal();

  if (!activeModal) {
    return null;
  }

  switch (activeModal.type) {
    case "create-folder":
      return (
        <CreateFolderBottomSheet
          roomId={roomId}
          accessToken={accessToken}
          onClose={closeModal}
          onSuccess={activeModal.resolve}
        />
      );

    default:
      return null;
  }
};
