import type { GalleryItem } from "@/entities/media";
import { GalleryMediaCard } from "./GalleryMediaCard";
import { GalleryGrid, GallerySection, StateMessage } from "./PhotoGallery.styles";

interface PhotoGalleryProps {
  items: GalleryItem[];
  userId: number;
  selectedPhotoIds: number[];
  isPending: boolean;
  isError: boolean;
  onTogglePhoto: (photoId: number) => void;
  onOpenPhoto?: (mediaId: number) => void;
}

export const PhotoGallery = ({
  items,
  userId,
  selectedPhotoIds,
  isPending,
  isError,
  onTogglePhoto,
  onOpenPhoto,
}: PhotoGalleryProps) => {
  if (isPending && items.length === 0)
    return <StateMessage>사진을 불러오는 중이에요.</StateMessage>;
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
              onOpen={
                (item.type === "local"
                  ? !item.file.type.startsWith("video/")
                  : item.media.type === "IMAGE") && onOpenPhoto
                  ? () => onOpenPhoto(item.mediaId)
                  : undefined
              }
            />
          );
        })}
      </GalleryGrid>
    </GallerySection>
  );
};
