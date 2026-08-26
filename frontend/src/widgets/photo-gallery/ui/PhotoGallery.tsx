import { MediaCard } from "@/entities/media";
import type { MediaItem } from "@/entities/media";
import { GalleryGrid, GallerySection, StateMessage } from "./PhotoGallery.styles";

interface PhotoGalleryProps {
  photos: MediaItem[];
  userId: number;
  selectedPhotoIds: number[];
  isPending: boolean;
  isError: boolean;
  onTogglePhoto: (photoId: number) => void;
}

export const PhotoGallery = ({
  photos,
  userId,
  selectedPhotoIds,
  isPending,
  isError,
  onTogglePhoto,
}: PhotoGalleryProps) => {
  if (isPending) return <StateMessage>사진을 불러오는 중이에요.</StateMessage>;
  if (isError) return <StateMessage>사진을 불러오지 못했어요.</StateMessage>;
  if (photos.length === 0) return <StateMessage>조건에 맞는 사진이 없어요.</StateMessage>;

  return (
    <GallerySection>
      <GalleryGrid>
        {photos.map((photo) => {
          const isSelected = selectedPhotoIds.includes(photo.mediaId);

          return (
            <MediaCard
              key={photo.mediaId}
              media={photo}
              isMine={photo.uploaderId === userId}
              isSelected={isSelected}
              onToggle={() => onTogglePhoto(photo.mediaId)}
            />
          );
        })}
      </GalleryGrid>
    </GallerySection>
  );
};
