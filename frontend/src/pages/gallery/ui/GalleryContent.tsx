import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey } from "@/entities/media";
import type { Room } from "@/entities/room";
import { SelectionDownloadBar } from "@/features/download-media";
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
  const queryClient = useQueryClient();
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

  // 고른 순서가 아니라 **화면에 보이는 순서**로 넘긴다. 압축을 풀었을 때 파일이
  // 갤러리와 같은 차례로 놓여야, 고른 순서를 기억하지 못하는 사용자가 헤매지 않는다.
  const downloadTargets = photos
    .filter((photo) => selectedPhotoIds.includes(photo.mediaId))
    .map((photo) => ({
      mediaId: photo.mediaId,
      fileName: photo.fileName,
      size: photo.size,
      mimeType: photo.mimeType,
    }));

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
      <MediaUploader
        roomId={room.roomId}
        token={accessToken}
        folderIds={selectedFolderId === null ? undefined : [selectedFolderId]}
        // 올린 사진은 서버에만 있다. 목록을 다시 불러오지 않으면 갤러리에 나타나지 않는다.
        onUploaded={() =>
          queryClient.invalidateQueries({ queryKey: photosQueryKey(room.roomId, userId) })
        }
      />
      <PhotoGallery
        photos={photos}
        userId={userId}
        selectedPhotoIds={selectedPhotoIds}
        isPending={isPending}
        isError={isError}
        onTogglePhoto={togglePhoto}
      />
      <SelectionDownloadBar
        targets={downloadTargets}
        roomId={room.roomId}
        roomCode={room.code}
        token={accessToken}
        onClearSelection={clearSelection}
      />
    </Page>
  );
};
