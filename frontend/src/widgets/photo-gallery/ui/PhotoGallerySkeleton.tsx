import { useDelayedVisibility } from "@/shared/hooks";
import { SPINNER_DELAY_MS } from "@/shared/ui/spinner";
import { GalleryGrid, GallerySection, HiddenLabel, SkeletonCard } from "./PhotoGallery.styles";

export const MAX_SKELETON_CARD_COUNT = 12;

interface PhotoGallerySkeletonProps {
  count?: number;
}

export const PhotoGallerySkeleton = ({
  count = MAX_SKELETON_CARD_COUNT,
}: PhotoGallerySkeletonProps) => {
  const isVisible = useDelayedVisibility(SPINNER_DELAY_MS);
  const cardCount = Math.max(0, Math.min(count, MAX_SKELETON_CARD_COUNT));

  return (
    <GallerySection role="status">
      <HiddenLabel>사진을 불러오는 중이에요.</HiddenLabel>
      {isVisible && cardCount > 0 && (
        <GalleryGrid aria-hidden>
          {Array.from({ length: cardCount }, (_, index) => (
            <SkeletonCard key={index} />
          ))}
        </GalleryGrid>
      )}
    </GallerySection>
  );
};
