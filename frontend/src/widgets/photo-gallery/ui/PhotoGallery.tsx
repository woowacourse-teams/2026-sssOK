import type { GalleryItem } from "@/entities/media";
import { GalleryMediaCard } from "./GalleryMediaCard";
import { GalleryGrid, GallerySection, StateMessage } from "./PhotoGallery.styles";
import { PhotoGallerySkeleton } from "./PhotoGallerySkeleton";

interface PhotoGalleryProps {
  items: GalleryItem[];
  userId: number;
  selectedPhotoIds: number[];
  isPending: boolean;
  isError: boolean;
  expectedPhotoCount?: number;
  onTogglePhoto: (photoId: number) => void;
  onOpenPhoto?: (mediaId: number) => void;
}

export const PhotoGallery = ({
  items,
  userId,
  selectedPhotoIds,
  isPending,
  isError,
  expectedPhotoCount,
  onTogglePhoto,
  onOpenPhoto,
}: PhotoGalleryProps) => {
  if (isPending && items.length === 0) return <PhotoGallerySkeleton count={expectedPhotoCount} />;
  if (isError && items.length === 0) return <StateMessage>사진을 불러오지 못했어요.</StateMessage>;
  if (items.length === 0) return <StateMessage>조건에 맞는 사진이 없어요.</StateMessage>;

  return (
    <GallerySection>
      <GalleryGrid>
        {items.map((item) => {
          const isSelected = selectedPhotoIds.includes(item.mediaId);

          return (
            <GalleryMediaCard
              key={item.mediaId}
              item={item}
              userId={userId}
              isSelected={isSelected}
              onToggle={() => onTogglePhoto(item.mediaId)}
              onOpen={onOpenPhoto ? () => onOpenPhoto(item.mediaId) : undefined}
            />
          );
        })}
      </GalleryGrid>
    </GallerySection>
  );
};
