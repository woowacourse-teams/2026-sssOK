import type { Room } from "@/entities/room";
import { MediaUploader } from "@/features/upload-media";
import { FolderFilter } from "@/widgets/folder-filter";
import { GalleryOptions } from "@/widgets/gallery-options";
import { PhotoGallery } from "@/widgets/photo-gallery";
import { RoomSummary } from "@/widgets/room-summary";
import { useGalleryFilter } from "../model/useGalleryFilter";
import { useGalleryPhotos } from "../model/useGalleryPhotos";
import { usePhotoSelection } from "../model/usePhotoSelection";
import { Page } from "./GalleryPage.styles";

interface GalleryContentProps {
  room: Room;
  accessToken: string;
  userId: number;
}

export const GalleryContent = ({ room, accessToken, userId }: GalleryContentProps) => {
  const { selectedFolderId, selectedOption, selectFolder, selectOption } = useGalleryFilter();

  // 사진 조회
  const { photos, isPending, isError } = useGalleryPhotos({
    roomId: room.roomId,
    accessToken,
    userId,
    selectedFolderId,
    selectedOption,
  });

  const photoIds = photos.map((photo) => photo.mediaId);
  const { selectedPhotoIds, isAllSelected, togglePhoto, toggleAllPhotos, clearSelection } =
    usePhotoSelection(photoIds);

  return (
    <Page>
      <RoomSummary
        roomCode={room.code}
        hostId={room.hostId}
        expiresAt={room.expiresAt}
        roomName={room.name}
      />
      <FolderFilter
        totalCount={room.photoCount}
        folders={room.folders}
        selectedFolderId={selectedFolderId}
        onSelectFolder={(folderId) => {
          selectFolder(folderId);
          clearSelection();
        }}
      />
      <GalleryOptions
        selectedOption={selectedOption}
        onSelectOption={(option) => {
          selectOption(option);
          clearSelection();
        }}
        isAllSelected={isAllSelected}
        canSelectAll={photoIds.length > 0}
        onToggleAll={toggleAllPhotos}
      />
      <MediaUploader />
      <PhotoGallery
        photos={photos}
        userId={userId}
        selectedPhotoIds={selectedPhotoIds}
        isPending={isPending}
        isError={isError}
        onTogglePhoto={togglePhoto}
      />
    </Page>
  );
};
