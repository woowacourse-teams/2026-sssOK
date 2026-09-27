import { MediaCard } from "@/entities/media";
import type { MediaItem } from "@/entities/media";
import type { GalleryItem } from "@/entities/media";
import { GalleryGrid, GallerySection, StateMessage } from "./PhotoGallery.styles";
import { PendingMediaCard } from "./PendingMediaCard";

interface PhotoGalleryProps {
  photos: MediaItem[];
  uploadSlots?: GalleryItem[];
  userId: number;
  selectedPhotoIds: number[];
  isPending: boolean;
  isError: boolean;
  onTogglePhoto: (photoId: number) => void;
  onOpenPhoto?: (mediaId: number) => void;
}

export const PhotoGallery = ({
  photos,
  uploadSlots = [],
  userId,
  selectedPhotoIds,
  isPending,
  isError,
  onTogglePhoto,
  onOpenPhoto,
}: PhotoGalleryProps) => {
  const uploadSlotIds = new Set(uploadSlots.map((slot) => slot.mediaId));
  const serverOnlyPhotos = photos.filter((photo) => !uploadSlotIds.has(photo.mediaId));

  if (isPending && uploadSlots.length === 0 && photos.length === 0)
    return <StateMessage>사진을 불러오는 중이에요.</StateMessage>;
  if (isError && uploadSlots.length === 0 && photos.length === 0)
    return <StateMessage>사진을 불러오지 못했어요.</StateMessage>;
  if (uploadSlots.length === 0 && photos.length === 0)
    return <StateMessage>조건에 맞는 사진이 없어요.</StateMessage>;

  return (
    <GallerySection>
      <GalleryGrid>
        {uploadSlots.map((slot) => {
          const isSelected = selectedPhotoIds.includes(slot.mediaId);

          return (
            <PendingMediaCard
              key={slot.mediaId}
              slot={slot}
              isSelected={isSelected}
              onToggle={() => onTogglePhoto(slot.mediaId)}
              onOpen={
                (slot.type === "local"
                  ? !slot.file.type.startsWith("video/")
                  : slot.media.type === "IMAGE") && onOpenPhoto
                  ? () => onOpenPhoto(slot.mediaId)
                  : undefined
              }
            />
          );
        })}

        {serverOnlyPhotos.map((photo) => {
          const isSelected = selectedPhotoIds.includes(photo.mediaId);

          return (
            <MediaCard
              key={photo.mediaId}
              media={photo}
              isMine={photo.uploaderId === userId}
              isSelected={isSelected}
              onToggle={() => onTogglePhoto(photo.mediaId)}
              onOpen={
                photo.type === "IMAGE" && onOpenPhoto ? () => onOpenPhoto(photo.mediaId) : undefined
              }
            />
          );
        })}
      </GalleryGrid>
    </GallerySection>
  );
};
