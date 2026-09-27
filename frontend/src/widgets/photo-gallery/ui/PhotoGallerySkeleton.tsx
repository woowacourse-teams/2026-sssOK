import { useDelayedVisibility } from "@/shared/hooks";
import { SPINNER_DELAY_MS } from "@/shared/ui/spinner";
import { GalleryGrid, GallerySection, HiddenLabel, SkeletonCard } from "./PhotoGallery.styles";

const SKELETON_CARD_COUNT = 12;

export const PhotoGallerySkeleton = () => {
  const isVisible = useDelayedVisibility(SPINNER_DELAY_MS);

  return (
    <GallerySection role="status">
      <HiddenLabel>사진을 불러오는 중이에요.</HiddenLabel>
      {isVisible && (
        <GalleryGrid aria-hidden>
          {Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
            <SkeletonCard key={index} />
          ))}
        </GalleryGrid>
      )}
    </GallerySection>
  );
};
